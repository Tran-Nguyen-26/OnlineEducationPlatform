package com.oep.corelms.contract;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M2CourseOpenApiContractTest {
    private static final Set<String> IMPLEMENTED_OPERATIONS = Set.of(
            "listPublicCourses", "createCourse", "getCourse", "updateCourse",
            "publishCourse", "unpublishCourse", "archiveCourse", "restoreCourse",
            "disableCourse", "enableCourse", "getCourseOutline", "listTeachingCourses",
            "listCourseInstructors", "addCoInstructor", "removeCoInstructor",
            "listModules", "createModule", "getModule", "updateModule", "reorderModules",
            "listLessons", "createLesson", "getLesson", "updateLesson", "reorderLessons",
            "listLessonContents", "createLessonContent", "getLessonContent", "updateLessonContent",
            "deleteLessonContent", "issueUploadUrl", "completeContentUpload",
            "getContentAccessUrl");

    private static final Set<String> PLANNED_OPERATIONS = Set.of(
            "deleteDraftCourse", "deleteModule", "deleteLesson", "reorderLessonContents");

    @Test
    void implementedCourseOperationsMatchTheOpenApiInventory() throws IOException {
        Map<String, Object> spec = loadSpec();
        assertEquals("3.0.3", spec.get("openapi"));

        Map<String, Object> paths = asMap(spec.get("paths"));
        Set<String> documentedOperations = new HashSet<>();
        Set<String> explicitlyPlannedOperations = new HashSet<>();

        paths.values().forEach(pathItem -> asMap(pathItem).forEach((method, rawOperation) -> {
            if (!Set.of("get", "post", "put", "patch", "delete").contains(method)) return;
            Map<String, Object> operation = asMap(rawOperation);
            String operationId = (String) operation.get("operationId");
            assertNotNull(operationId, "Every M2 operation must have an operationId");
            assertTrue(operation.get("responses") instanceof Map<?, ?> && !asMap(operation.get("responses")).isEmpty(),
                    () -> "Missing responses for " + operationId);
            assertTrue(documentedOperations.add(operationId), "Duplicate operationId: " + operationId);
            if ("planned".equals(operation.get("x-implementation-status"))) {
                explicitlyPlannedOperations.add(operationId);
            }
        }));

        assertEquals(PLANNED_OPERATIONS, explicitlyPlannedOperations,
                "Unimplemented routes must be clearly marked planned in the contract");
        Set<String> accountedOperations = new HashSet<>(IMPLEMENTED_OPERATIONS);
        accountedOperations.addAll(PLANNED_OPERATIONS);
        assertEquals(accountedOperations, documentedOperations,
                "Update the implementation inventory when the M2 API contract changes");
    }

    @Test
    void uploadContractKeepsObjectKeysServerManagedAndCompletionBodyless() throws IOException {
        Map<String, Object> spec = loadSpec();
        Map<String, Object> paths = asMap(spec.get("paths"));
        Map<String, Object> uploadUrl = operation(paths, "/contents/{contentId}/upload-url", "post");
        Map<String, Object> completeUpload = operation(paths, "/contents/{contentId}/complete-upload", "post");

        assertFalse(uploadUrl.containsKey("requestBody"));
        assertFalse(completeUpload.containsKey("requestBody"));

        Map<String, Object> schemas = asMap(asMap(spec.get("components")).get("schemas"));
        assertFalse(asMap(asMap(schemas.get("UploadUrlResponse")).get("properties")).containsKey("object_key"));
        assertFalse(asMap(asMap(schemas.get("Content")).get("properties")).containsKey("object_key"));
        assertTrue(String.valueOf(asMap(schemas.get("CreateContent")).get("description")).contains("100 MiB"));
    }

    private static Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return asMap(asMap(paths.get(path)).get(method));
    }

    private static Map<String, Object> loadSpec() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path spec = directory.resolve("openapi/m2-course.yaml");
            if (Files.isRegularFile(spec)) {
                LoaderOptions options = new LoaderOptions();
                options.setAllowDuplicateKeys(false);
                Object parsed = new Yaml(new SafeConstructor(options)).load(Files.readString(spec));
                return asMap(parsed);
            }
            directory = directory.getParent();
        }
        throw new IOException("Could not locate openapi/m2-course.yaml from the test working directory");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertTrue(value instanceof Map<?, ?>, "Expected a mapping in the OpenAPI document");
        return (Map<String, Object>) value;
    }
}
