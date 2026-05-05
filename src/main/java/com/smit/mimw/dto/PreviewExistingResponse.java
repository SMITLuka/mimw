package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for GET /mimv/preview/existing.
 * Contains all existing MIMV_ZAGLAVLJE rows for the current company.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewExistingResponse {

    private List<MimvProcessedItem> mimvProcessed;
}
