package com.contractor.app.accounting.domain;

import java.time.LocalDate;
import java.util.List;

public class JournalPostingRequest {

    private String postingKey;
    private String sourceType;
    private Long sourceId;
    private LocalDate accountingDate;
    private String description;
    private Long reversalOfEntryId;
    private List<JournalLineRequest> lines;

    public JournalPostingRequest() {
    }

    public JournalPostingRequest(String postingKey, String sourceType, Long sourceId, LocalDate accountingDate,
                                 String description, Long reversalOfEntryId, List<JournalLineRequest> lines) {
        this.postingKey = postingKey;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.accountingDate = accountingDate;
        this.description = description;
        this.reversalOfEntryId = reversalOfEntryId;
        this.lines = lines;
    }

    public String getPostingKey() {
        return postingKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public LocalDate getAccountingDate() {
        return accountingDate;
    }

    public String getDescription() {
        return description;
    }

    public Long getReversalOfEntryId() {
        return reversalOfEntryId;
    }

    public List<JournalLineRequest> getLines() {
        return lines;
    }
}
