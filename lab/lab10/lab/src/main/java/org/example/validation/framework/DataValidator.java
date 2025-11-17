package org.example.validation.framework;
import org.example.validation.annotations.*;
import org.example.validation.model.User;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Scanner;

public class DataValidator {
    public static boolean validate(Object obj) {
        if (obj == null) {
            System.out.println("Object is null");
            return false;
        }

        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        Map<String, Object> fieldValues = new HashMap<>();
        for (Field f : fields) {
            f.setAccessible(true);
            try {
                fieldValues.put(f.getName(), f.get(obj));
            } catch (IllegalAccessException ignored) {}
        }

        boolean allPass = true;
        String usernameVal = fieldValues.getOrDefault("username", null) instanceof String
                ? (String) fieldValues.get("username") : null;
        for (Field field : fields) {
            field.setAccessible(true);
            Object valueObj;
            try {
                valueObj = field.get(obj);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (!(valueObj instanceof String)) {
                continue;
            }
            String value = (String) valueObj;
            String fieldName = field.getName();

            // @MinLength
            MinLength minLength = field.getAnnotation(MinLength.class);
            if (minLength != null) {
                int min = minLength.min();
                if (value.length() < min) {
                    System.out.printf("Validation failed for field *%s*: should have a minimum length of %d%n", fieldName, min);
                    allPass = false;
                }
            }

            // @CustomValidation
            CustomValidation[] validations = field.getAnnotationsByType(CustomValidation.class);
            for (CustomValidation cv : validations) {
                Rule rule = cv.rule();
                switch (rule) {
                    case ALL_LOWERCASE -> {
                        if (!value.equals(value.toLowerCase())) {
                            System.out.printf("Validation failed for field *%s*: should be all lowercase%n", fieldName);
                            allPass = false;
                        }
                    }
                    case NO_USERNAME -> {
                        if (usernameVal != null && !usernameVal.isBlank()
                                && !fieldName.equals("username")
                                && value.contains(usernameVal)) {
                            System.out.printf("Validation failed for field *%s*: should not contain username%n", fieldName);
                            allPass = false;
                        }
                    }
                    case HAS_BOTH_DIGITS_AND_LETTERS -> {
                        boolean hasDigit = false;
                        boolean hasLetter = false;
                        for (char c : value.toCharArray()) {
                            if (Character.isDigit(c)) hasDigit = true;
                            if (Character.isLetter(c)) hasLetter = true;
                            if (hasDigit && hasLetter) break;
                        }
                        if (!(hasDigit && hasLetter)) {
                            System.out.printf("Validation failed for field *%s*: should have both letters and digits%n", fieldName);
                            allPass = false;
                        }
                    }
                }
            }
        }
        return allPass;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("Username: ");
            String username = sc.nextLine().trim();

            System.out.print("Password: ");
            String pwd = sc.nextLine().trim();

            User user = new User(username, pwd);
            if (validate(user)) {
                System.out.println("Success!");
                break;
            }
        }
    }
}