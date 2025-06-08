/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Manages static verification codes for phone numbers.
 * When a phone number is first used, the verification code provided is stored as the password.
 * Subsequent verifications must use the same code.
 */
public class StaticVerificationCodeManager {

  private static final Logger logger = LoggerFactory.getLogger(StaticVerificationCodeManager.class);

  public static final String KEY_PHONE_NUMBER = "phone_number";
  public static final String ATTR_VERIFICATION_CODE = "verification_code";
  public static final String ATTR_CREATED_AT = "created_at";
  public static final String ATTR_UPDATED_AT = "updated_at";

  private final DynamoDbClient dynamoDbClient;
  private final String tableName;

  public StaticVerificationCodeManager(final DynamoDbClient dynamoDbClient, final String tableName) {
    this.dynamoDbClient = dynamoDbClient;
    this.tableName = tableName;
  }

  /**
   * Verifies a verification code for a phone number.
   * If the phone number doesn't exist, creates a new record with the provided code.
   * If the phone number exists, compares the provided code with the stored code.
   *
   * @param phoneNumber the E164 formatted phone number
   * @param inputCode   the verification code to verify
   * @return true if verification is successful, false otherwise
   */
  public boolean verifyAndStore(final String phoneNumber, final String inputCode) {
    try {
      final Optional<String> storedCode = getStoredCode(phoneNumber);

      if (storedCode.isEmpty()) {
        // First time use, create new record
        createNewRecord(phoneNumber, inputCode);
        logger.info("Created new static verification code record for phone number: {}", phoneNumber);
        return true;
      } else {
        // Phone number exists, verify the code
        final boolean matches = storedCode.get().equals(inputCode);
        if (matches) {
          logger.debug("Static verification code verified successfully for phone number: {}", phoneNumber);
        } else {
          logger.warn("Static verification code mismatch for phone number: {}", phoneNumber);
        }
        return matches;
      }
    } catch (final Exception e) {
      logger.error("Error verifying static verification code for phone number: {}", phoneNumber, e);
      return false;
    }
  }

  /**
   * Retrieves the stored verification code for a phone number.
   *
   * @param phoneNumber the E164 formatted phone number
   * @return the stored verification code, or empty if not found
   */
  public Optional<String> getStoredCode(final String phoneNumber) {
    try {
      final GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
          .tableName(tableName)
          .key(Map.of(KEY_PHONE_NUMBER, AttributeValue.builder().s(phoneNumber).build()))
          .build());

      if (response.item().isEmpty()) {
        return Optional.empty();
      } else {
        final AttributeValue codeValue = response.item().get(ATTR_VERIFICATION_CODE);
        return codeValue != null ? Optional.of(codeValue.s()) : Optional.empty();
      }
    } catch (final Exception e) {
      logger.error("Error retrieving static verification code for phone number: {}", phoneNumber, e);
      return Optional.empty();
    }
  }

  /**
   * Creates a new record with phone number and verification code.
   *
   * @param phoneNumber the E164 formatted phone number
   * @param code        the verification code to store
   */
  private void createNewRecord(final String phoneNumber, final String code) {
    final long now = Instant.now().getEpochSecond();

    dynamoDbClient.putItem(PutItemRequest.builder()
        .tableName(tableName)
        .item(Map.of(
            KEY_PHONE_NUMBER, AttributeValue.builder().s(phoneNumber).build(),
            ATTR_VERIFICATION_CODE, AttributeValue.builder().s(code).build(),
            ATTR_CREATED_AT, AttributeValue.builder().n(String.valueOf(now)).build(),
            ATTR_UPDATED_AT, AttributeValue.builder().n(String.valueOf(now)).build()
        ))
        .build());
  }
} 