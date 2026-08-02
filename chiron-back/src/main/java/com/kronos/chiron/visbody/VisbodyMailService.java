package com.kronos.chiron.visbody;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class VisbodyMailService {

    private static final Logger log = LoggerFactory.getLogger(VisbodyMailService.class);

    private final VisbodyImportService importService;

    @Value("${chiron.visbody.mailbox.enabled:false}")
    private boolean enabled;
    @Value("${chiron.visbody.mailbox.host:imap.gmail.com}")
    private String host;
    @Value("${chiron.visbody.mailbox.port:993}")
    private int port;
    @Value("${chiron.visbody.mailbox.username:}")
    private String username;
    @Value("${chiron.visbody.mailbox.password:}")
    private String password;
    @Value("${chiron.visbody.mailbox.folder:INBOX}")
    private String folderName;

    @Scheduled(fixedDelayString = "${chiron.visbody.mailbox.poll-interval-ms:300000}")
    public void pollMailbox() {
        if (!enabled) return;
        if (username.isBlank() || password.isBlank()) {
            log.warn("Visbody mailbox activée mais identifiants IMAP manquants — relève ignorée.");
            return;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.ssl.enable", "true");

        Store store = null;
        Folder folder = null;
        try {
            Session session = Session.getInstance(props);
            store = session.getStore("imaps");
            store.connect(host, username, password);

            folder = store.getFolder(folderName);
            folder.open(Folder.READ_WRITE);

            Message[] unread = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (Message message : unread) {
                try {
                    processMessage(message);
                } catch (Exception e) {
                    log.warn("Visbody : échec traitement d'un mail : {}", e.getMessage());
                } finally {
                    message.setFlag(Flags.Flag.SEEN, true);
                }
            }
        } catch (Exception e) {
            log.warn("Visbody : relève IMAP impossible : {}", e.getMessage());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void processMessage(Message message) throws Exception {
        String sender = senderAddress(message);
        List<byte[]> pdfs = extractPdfAttachments(message);
        if (pdfs.isEmpty()) return;

        for (byte[] pdf : pdfs) {
            VisbodyImportService.ImportResult result = importService.importFromEmail(pdf, sender);
            log.info("Visbody mail de {} : {} — {}", sender, result.outcome(), result.detail());
        }
    }

    private String senderAddress(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) return null;
        if (from[0] instanceof InternetAddress ia) return ia.getAddress();
        return from[0].toString();
    }

    private List<byte[]> extractPdfAttachments(Part part) throws Exception {
        List<byte[]> result = new ArrayList<>();
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                result.addAll(extractPdfAttachments(multipart.getBodyPart(i)));
            }
        } else if (isPdf(part)) {
            try (InputStream in = part.getInputStream()) {
                result.add(in.readAllBytes());
            }
        }
        return result;
    }

    private boolean isPdf(Part part) throws MessagingException {
        String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase();
        if (contentType.contains("application/pdf")) return true;
        String filename = part.getFileName();
        if (filename != null) {
            try {
                filename = MimeUtility.decodeText(filename);
            } catch (Exception ignored) {
            }
            return filename.toLowerCase().endsWith(".pdf");
        }
        return false;
    }

    private void closeQuietly(Folder folder) {
        try {
            if (folder != null && folder.isOpen()) folder.close(false);
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Store store) {
        try {
            if (store != null && store.isConnected()) store.close();
        } catch (Exception ignored) {
        }
    }
}
