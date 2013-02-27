package pl.edu.icm.cermine.web.controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import static java.util.Collections.singletonList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import pl.edu.icm.cermine.service.CermineExtractorService;
import pl.edu.icm.cermine.service.ExtractionResult;
import pl.edu.icm.cermine.service.ExtractionTask;
import pl.edu.icm.cermine.service.NoSuchTaskException;
import pl.edu.icm.cermine.service.TaskManager;

/**
 *
 * @author bart
 * @author axnow
 */
@org.springframework.stereotype.Controller
public class CermineController {

    @Autowired
    CermineExtractorService extractorService;
    @Autowired
    TaskManager taskManager;
    Logger logger = LoggerFactory.getLogger(CermineController.class);

    @RequestMapping(value = "/index.html")
    public String showHome(Model model) {
        return "home";
    }

    @RequestMapping(value = "/about.html")
    public String showAbout(Model model) {
        return "about";
    }

    @RequestMapping(value = "/download.html")
    public ResponseEntity<String> downloadXML(@RequestParam("task") long taskId,
            @RequestParam("type") String resultType, Model model) throws NoSuchTaskException {
        ExtractionTask task = taskManager.getTask(taskId);
        if ("nlm".equals(resultType)) {
            String nlm = task.getResult().getNlm();
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_XML);
            return new ResponseEntity<String>(nlm, responseHeaders, HttpStatus.OK);
        } else {
            throw new RuntimeException("Unknown request type: " + resultType);
        }
    }

    @RequestMapping(value = "/upload.do", method = RequestMethod.POST)
    public String uploadFileStream(@RequestParam("files") MultipartFile file, HttpServletRequest request, Model model) {
        logger.info("Got an upload request.");
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                model.addAttribute("warning", "An empty or no file sent.");
                return "home";
            }
            String filename = file.getOriginalFilename();
            logger.debug("Original filename is: " + filename);
            filename = taskManager.getProperFilename(filename);
            logger.debug("Created filename: " + filename);
            long taskId = extractorService.initExtractionTask(content, filename);
            logger.debug("Task manager is: " + taskManager);
            return "redirect:/task.html?task=" + taskId;

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @RequestMapping(value = "/extract.do", method = RequestMethod.POST)
    public ResponseEntity<String> extractSync(@RequestBody byte[] content,
            HttpServletRequest request,
            Model model) {
        try {
            logger.debug("content length: {}", content.length);
            
//            byte[] content = file.getBytes();
//            if (content.length == 0) {
//                model.addAttribute("warning", "An empty or no file sent.");
//                return new ResponseEntity<String>("Invalid or empty file", null, HttpStatus.BAD_REQUEST);
//            }
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_XML);
            ExtractionResult result = extractorService.extractNLM(new ByteArrayInputStream(content));
            String nlm = result.getNlm();
            return new ResponseEntity<String>(nlm, responseHeaders, HttpStatus.OK);
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(CermineController.class.getName()).log(Level.SEVERE, null, ex);
            return new ResponseEntity<String>("Exception: " + ex.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @ExceptionHandler(value = NoSuchTaskException.class)
    public ModelAndView taskNotFoundHandler(NoSuchTaskException nste) {
        return new ModelAndView("error", "errorMessage", nste.getMessage());
    }

    @RequestMapping(value = "/task.html", method = RequestMethod.GET)
    public ModelAndView showTask(@RequestParam("task") long id) throws NoSuchTaskException {
        ExtractionTask task = taskManager.getTask(id);

        HashMap<String, Object> model = new HashMap<String, Object>();
        model.put("task", task);
        if (task.isFinished()) {
            model.put("result", task.getResult());
            String nlmHtml = StringEscapeUtils.escapeHtml(task.getResult().getNlm());
            model.put("nlm", nlmHtml);
            model.put("meta", task.getResult().getMeta());
            model.put("html", task.getResult().getHtml());
        }
        return new ModelAndView("task", model);
    }

    @RequestMapping(value = "/tasks.html")
    public ModelAndView showTasks() {
        return new ModelAndView("tasks", "tasks", taskManager.taskList());
    }

    private static ResponseEntity<List<Map<String, Object>>> uploadResponseOK(MultipartFile file, int size) {
        return wrapResponse(fileDetails(file, size));
    }

    private static ResponseEntity<List<Map<String, Object>>> wrapResponse(Map<String, Object> rBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return new ResponseEntity<List<Map<String, Object>>>(singletonList(rBody), headers, HttpStatus.OK);
    }

    private static Map<String, Object> fileDetails(MultipartFile file, int size) {
        Map<String, Object> rBody = new HashMap<String, Object>();
        rBody.put("name", file.getOriginalFilename());
        rBody.put("size", size);
        return rBody;
    }

    public CermineExtractorService getExtractorService() {
        return extractorService;
    }

    public void setExtractorService(CermineExtractorService extractorService) {
        this.extractorService = extractorService;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }
}