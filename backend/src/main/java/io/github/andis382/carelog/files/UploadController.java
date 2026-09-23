package io.github.andis382.carelog.files;

import io.github.andis382.carelog.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photos (a note's photo, a prescription, a medicine box) are uploaded first and then
 * referenced by id, so the JSON endpoints stay JSON.
 */
@RestController
public class UploadController {

    private final FileStorage storage;
    private final CurrentUser currentUser;

    public UploadController(FileStorage storage, CurrentUser currentUser) {
        this.storage = storage;
        this.currentUser = currentUser;
    }

    public record Uploaded(String id, String url) {}

    @PostMapping("/api/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public Uploaded upload(@RequestParam("file") MultipartFile file) {
        currentUser.requireRecorder();
        StoredFile stored = storage.store(currentUser.organizationId(), file, FileStorage.IMAGES);
        return new Uploaded(stored.getId(), fileUrl(stored.getId()));
    }

    public static String fileUrl(String fileId) {
        return fileId == null ? null : "/api/files/" + fileId;
    }
}
