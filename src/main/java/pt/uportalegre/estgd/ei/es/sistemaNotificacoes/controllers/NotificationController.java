package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Priority;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.*;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("notifications", notificationService.findAll());
        return "list";
    }

    @GetMapping("/new")
    public String showForm(Model model) {
        populateFormModel(model);
        return "form";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Integer id, Model model) {
        try {
            NotificationDTO notification = notificationService.findById(id);
            model.addAttribute("notification", notification);
            return "detail";
        } catch (NoSuchElementException e) {
            log.warn("Notification #{} not found, redirecting to list", id);
            return "redirect:/notifications";
        }
    }

    @PostMapping("/send")
    public String create(
            @RequestParam ChannelType channelType,
            @RequestParam(required = false) Priority priority,
            @RequestParam String content,
            @RequestParam Map<String, String> allParams,
            Model model) {
        log.info("Received notification creation request: channel={}, priority={}", channelType, priority);
        try {
            NotificationRequestDTO requestDto = NotificationRequestDTO.fromRequestParams(channelType, priority, content, allParams);
            NotificationDTO dto = requestDto.toNotificationDTO();
            NotificationDTO created = notificationService.processNotification(dto);
            log.info("Notification #{} created with status {}", created.id(), created.status());
            return "redirect:/notifications/" + created.id();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid notification request: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            populateFormModel(model);
            return "form";
        }
    }

    private void populateFormModel(Model model) {
        model.addAttribute("channels", ChannelType.values());
        model.addAttribute("priorities", Priority.values());
    }
}
