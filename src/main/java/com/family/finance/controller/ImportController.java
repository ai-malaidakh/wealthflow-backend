package com.family.finance.controller;

import com.family.finance.entity.*;
import com.family.finance.repository.AccountRepository;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.TransactionRepository;
import com.family.finance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    @PostMapping("/coinkeeper")
    @Transactional
    public ResponseEntity<ImportResponse> importCoinKeeper(
            @RequestParam("file") MultipartFile file,
            @RequestParam UUID accountId) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.REQUEST_ENTITY_TOO_LARGE, "File size exceeds 10MB limit");
        }

        List<String> errors = new ArrayList<>();
        int imported = 0;
        int duplicates = 0;

        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        Account account = accountRepository.findByIdAndTenant(accountId, familyIds, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty CSV file");
            }

            Map<String, Integer> columnMap = parseHeaders(headerLine);
            validateHeaders(columnMap);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    List<String> fields = parseLine(line);
                    if (fields.size() < 5) {
                        errors.add("Row has insufficient fields: " + line);
                        continue;
                    }

                    String dateStr = fields.get(columnMap.get("date"));
                    String amountStr = fields.get(columnMap.get("amount"));
                    String category = fields.get(columnMap.get("category"));
                    String description = fields.get(columnMap.get("description"));

                    LocalDate date = parseDate(dateStr);
                    BigDecimal amount = parseAmount(amountStr);

                    String importHash = computeImportHash(dateStr, amountStr, description);

                    if (transactionRepository.findByImportHash(importHash, familyIds, userId).isPresent()) {
                        duplicates++;
                        continue;
                    }

                    Transaction transaction = new Transaction();
                    transaction.setId(UUID.randomUUID());
                    transaction.setImportHash(importHash);

                    transaction.setAccount(account);

                    transaction.setAmount(amount);
                    transaction.setDescription(description);
                    transaction.setDate(date);

                    if (category != null && !category.trim().isEmpty()) {
                        Category cat = findOrCreateCategory(category, familyIds, userId);
                        transaction.setCategory(cat);
                    }

                    transactionRepository.save(transaction);
                    imported++;

                } catch (Exception e) {
                    errors.add("Failed to parse row: " + line + " - " + e.getMessage());
                }
            }

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", e);
        }

        return ResponseEntity.ok(new ImportResponse(imported, duplicates, errors));
    }

    private Map<String, Integer> parseHeaders(String headerLine) {
        Map<String, Integer> columnMap = new HashMap<>();
        String[] headers = headerLine.split(",");
        for (int i = 0; i < headers.length; i++) {
            columnMap.put(headers[i].trim().toLowerCase(), i);
        }
        return columnMap;
    }

    private void validateHeaders(Map<String, Integer> columnMap) {
        String[] requiredColumns = {"date", "amount", "category", "description", "account"};
        for (String column : requiredColumns) {
            if (!columnMap.containsKey(column)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Missing required column: " + column);
            }
        }
    }

    private List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());

        return fields;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date format: " + dateStr + " (expected YYYY-MM-DD)");
        }
    }

    private BigDecimal parseAmount(String amountStr) {
        try {
            return new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid amount: " + amountStr);
        }
    }

    private String computeImportHash(String date, String amount, String description) {
        try {
            String data = date + amount + description;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    private Category findOrCreateCategory(String categoryName, List<UUID> familyIds, UUID userId) {
        Optional<Category> categoryOpt = categoryRepository.findByNameAndTypeAndTenant(
                categoryName, Category.CategoryType.EXPENSE, familyIds, userId);
        
        if (categoryOpt.isPresent()) {
            return categoryOpt.get();
        }

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName(categoryName);
        category.setType(Category.CategoryType.EXPENSE);

        if (!familyIds.isEmpty() && familyIds.get(0) != null) {
            category.setFamily(new Family());
            category.getFamily().setId(familyIds.get(0));
        } else {
            category.setUser(new User());
            category.getUser().setId(userId);
        }

        return categoryRepository.save(category);
    }

    public record ImportResponse(int imported, int duplicates, List<String> errors) {}
}
