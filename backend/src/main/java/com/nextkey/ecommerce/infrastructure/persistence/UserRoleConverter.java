package com.nextkey.ecommerce.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.nextkey.ecommerce.domain.model.user.User.UserRole;

@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(final UserRole role) {
        if (role == null) {
            return null;
        }
        return role.name();
    }

    @Override
    public UserRole convertToEntityAttribute(final String dbData) {
        if (dbData == null) {
            return null;
        }
        return UserRole.valueOf(dbData);
    }
}