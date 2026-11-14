package com.oep.corelms.service;

import com.oep.corelms.config.MediaStorageConfig;
import com.oep.corelms.dto.identity.MyProfileDtos;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MyProfileService {
    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofMinutes(10);
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final ObjectProvider<S3Client> storageProvider;
    private final ObjectProvider<S3Presigner> signerProvider;
    private final ObjectProvider<MediaStorageConfig.Properties> propertiesProvider;

    @Transactional(readOnly = true)
    public MyProfileDtos.Response get(UUID actorId) {
        var user = requireActiveUser(actorId);
        UserProfile profile = profiles.findById(actorId).orElse(null);
        return response(user, profile);
    }

    @Transactional
    public MyProfileDtos.Response update(UUID actorId, MyProfileDtos.UpdateRequest request) {
        var user = requireActiveUser(actorId);
        UserProfile profile = profileFor(user);
        profile.setFullName(request.fullName().trim());
        profile.setPhone(clean(request.phone()));
        profile.setOrganization(clean(request.organization()));
        profile.setBio(clean(request.bio()));
        profile.setDateOfBirth(request.dateOfBirth());
        return response(user, profiles.saveAndFlush(profile));
    }

    @Transactional
    public MyProfileDtos.AvatarUploadTicket initiateAvatar(UUID actorId, MyProfileDtos.AvatarUploadRequest request) {
        requireActiveUser(actorId);
        AvatarFormat format = AvatarFormat.from(request.filename());
        if (request.size() < 1 || request.size() > MAX_AVATAR_SIZE) {
            throw invalid("Ảnh đại diện phải có kích thước tối đa 2 MB");
        }
        var signer = signerProvider.getIfAvailable();
        var properties = propertiesProvider.getIfAvailable();
        if (signer == null || properties == null) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE, "Kho lưu trữ ảnh chưa được cấu hình");
        }
        String key = "avatars/" + actorId + "/" + UUID.randomUUID() + "." + format.extension;
        var signed = signer.presignPutObject(builder -> builder.signatureDuration(URL_TTL)
                .putObjectRequest(put -> put.bucket(properties.bucket()).key(key)
                        .contentType(format.mime).contentLength(request.size())));
        return new MyProfileDtos.AvatarUploadTicket(key, signed.url().toExternalForm(), signed.signedHeaders());
    }

    @Transactional
    public MyProfileDtos.Response confirmAvatar(UUID actorId, MyProfileDtos.AvatarConfirmRequest request) {
        var user = requireActiveUser(actorId);
        String prefix = "avatars/" + actorId + "/";
        if (!request.objectKey().startsWith(prefix) || request.objectKey().contains("..")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Ảnh đại diện không hợp lệ");
        }
        var format = AvatarFormat.from(request.objectKey());
        var storage = storageProvider.getIfAvailable();
        var properties = propertiesProvider.getIfAvailable();
        if (storage == null || properties == null) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE, "Kho lưu trữ ảnh chưa được cấu hình");
        }
        try {
            var object = storage.headObject(head -> head.bucket(properties.bucket()).key(request.objectKey()));
            if (object.contentLength() == null || object.contentLength() < 1 || object.contentLength() > MAX_AVATAR_SIZE
                    || !format.mime.equalsIgnoreCase(object.contentType())) {
                throw invalid("Ảnh tải lên không hợp lệ hoặc vượt quá 2 MB");
            }
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) throw invalid("Chưa tìm thấy ảnh đã tải lên");
            throw ex;
        }
        UserProfile profile = profileFor(user);
        profile.setAvatarObjectKey(request.objectKey());
        return response(user, profiles.saveAndFlush(profile));
    }

    private MyProfileDtos.Response response(com.oep.coremodel.entity.identity.User user, UserProfile profile) {
        String avatarUrl = null;
        if (profile != null && profile.getAvatarObjectKey() != null) {
            var signer = signerProvider.getIfAvailable();
            var properties = propertiesProvider.getIfAvailable();
            if (signer != null && properties != null) {
                var signed = signer.presignGetObject(builder -> builder.signatureDuration(URL_TTL)
                        .getObjectRequest(get -> get.bucket(properties.bucket()).key(profile.getAvatarObjectKey())));
                avatarUrl = signed.url().toExternalForm();
            }
        }
        return new MyProfileDtos.Response(user.getId(), user.getEmail(), user.getRole().name(),
                profile == null ? user.getEmail() : profile.getFullName(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getOrganization(),
                profile == null ? null : profile.getBio(),
                profile == null ? null : profile.getDateOfBirth(), avatarUrl);
    }

    private UserProfile profileFor(com.oep.coremodel.entity.identity.User user) {
        return profiles.findById(user.getId()).orElseGet(() -> {
            UserProfile profile = new UserProfile();
            profile.setUser(user);
            profile.setFullName(user.getEmail());
            return profile;
        });
    }

    private com.oep.coremodel.entity.identity.User requireActiveUser(UUID actorId) {
        return users.findById(actorId).filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Tài khoản không hoạt động"));
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_FAILED, message); }

    private enum AvatarFormat {
        PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), WEBP("webp", "image/webp");
        private final String extension;
        private final String mime;
        AvatarFormat(String extension, String mime) { this.extension = extension; this.mime = mime; }
        static AvatarFormat from(String filename) {
            int dot = filename.lastIndexOf('.');
            if (dot < 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ chấp nhận ảnh PNG, JPG hoặc WebP");
            return switch (filename.substring(dot + 1).toLowerCase(Locale.ROOT)) {
                case "png" -> PNG;
                case "jpg", "jpeg" -> JPEG;
                case "webp" -> WEBP;
                default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ chấp nhận ảnh PNG, JPG hoặc WebP");
            };
        }
    }
}
