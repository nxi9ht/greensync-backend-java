package th.ac.rmutt.greensync.uploads;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import th.ac.rmutt.greensync.security.AuthenticatedUser;

@RestController
@RequestMapping("/uploads")
public class UploadsController {

  private final UploadsService uploadsService;

  public UploadsController(UploadsService uploadsService) {
    this.uploadsService = uploadsService;
  }

  @PostMapping
  public Map<String, Object> uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false, defaultValue = "evidence") String folder,
      @RequestParam(required = false) Integer assessmentDetailId,
      @RequestParam(required = false) Integer userId,
      @RequestParam(required = false) Integer carbonLogId,
      @RequestParam(required = false) String category,
      @AuthenticationPrincipal AuthenticatedUser me) {
    String cleanFolder = uploadsService.validateFolder(folder);
    Integer targetUserId = uploadsService.resolveTargetUserId(me, userId);
    return uploadsService.uploadFile(file, cleanFolder, assessmentDetailId, targetUserId, carbonLogId, category, me);
  }

  @GetMapping("/{id}")
  public Map<String, Object> getFile(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    return uploadsService.findOne(id, me);
  }

  @DeleteMapping("/{id}")
  public Map<String, Object> deleteFile(@PathVariable Integer id, @AuthenticationPrincipal AuthenticatedUser me) {
    return uploadsService.deleteFile(id, me);
  }

  @GetMapping
  public List<Map<String, Object>> getFiles(
      @AuthenticationPrincipal AuthenticatedUser me,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer limit) {
    return uploadsService.findAll(me, page, limit);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> updateFile(
      @PathVariable Integer id, @RequestBody Map<String, String> body, @AuthenticationPrincipal AuthenticatedUser me) {
    return uploadsService.update(id, body.get("category"), me);
  }
}
