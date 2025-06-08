/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.whispersystems.textsecuregcm.storage.DynamoDbExtensionSchema.Tables;

class StaticVerificationCodeManagerTest {

  @RegisterExtension
  static final DynamoDbExtension DYNAMO_DB_EXTENSION = new DynamoDbExtension(Tables.STATIC_VERIFICATION_CODES);

  private StaticVerificationCodeManager staticVerificationCodeManager;

  @BeforeEach
  void setUp() {
    staticVerificationCodeManager = new StaticVerificationCodeManager(
        DYNAMO_DB_EXTENSION.getDynamoDbClient(), 
        Tables.STATIC_VERIFICATION_CODES.tableName());
  }

  @Test
  void testFirstTimeVerification() {
    final String phoneNumber = "+18005551234";
    final String verificationCode = "123456";

    // First time verification should succeed and create new record
    final boolean result = staticVerificationCodeManager.verifyAndStore(phoneNumber, verificationCode);
    assertThat(result).isTrue();

    // Verify the code was stored
    final Optional<String> storedCode = staticVerificationCodeManager.getStoredCode(phoneNumber);
    assertThat(storedCode).isPresent();
    assertThat(storedCode.get()).isEqualTo(verificationCode);
  }

  @Test
  void testSubsequentVerificationWithSameCode() {
    final String phoneNumber = "+18005551234";
    final String verificationCode = "123456";

    // First verification
    staticVerificationCodeManager.verifyAndStore(phoneNumber, verificationCode);

    // Second verification with same code should succeed
    final boolean result = staticVerificationCodeManager.verifyAndStore(phoneNumber, verificationCode);
    assertThat(result).isTrue();
  }

  @Test
  void testSubsequentVerificationWithDifferentCode() {
    final String phoneNumber = "+18005551234";
    final String firstCode = "123456";
    final String secondCode = "654321";

    // First verification
    staticVerificationCodeManager.verifyAndStore(phoneNumber, firstCode);

    // Second verification with different code should fail
    final boolean result = staticVerificationCodeManager.verifyAndStore(phoneNumber, secondCode);
    assertThat(result).isFalse();
  }

  @Test
  void testDifferentPhoneNumbers() {
    final String phoneNumber1 = "+18005551234";
    final String phoneNumber2 = "+18005555678";
    final String code1 = "123456";
    final String code2 = "654321";

    // Verify different phone numbers can have different codes
    final boolean result1 = staticVerificationCodeManager.verifyAndStore(phoneNumber1, code1);
    final boolean result2 = staticVerificationCodeManager.verifyAndStore(phoneNumber2, code2);

    assertThat(result1).isTrue();
    assertThat(result2).isTrue();

    // Verify stored codes are correct
    assertThat(staticVerificationCodeManager.getStoredCode(phoneNumber1)).contains(code1);
    assertThat(staticVerificationCodeManager.getStoredCode(phoneNumber2)).contains(code2);
  }

  @Test
  void testGetStoredCodeForNonExistentNumber() {
    final String phoneNumber = "+18005551234";
    
    final Optional<String> storedCode = staticVerificationCodeManager.getStoredCode(phoneNumber);
    assertThat(storedCode).isEmpty();
  }
} 