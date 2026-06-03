package com.smit.mimw.service;

import com.smit.mimw.dto.Brand;
import com.smit.mimw.dto.BrandsResponse;
import com.smit.mimw.dto.CompanyData;
import com.smit.mimw.dto.FormBuildRequest;
import com.smit.mimw.dto.FormBuildResponse;
import com.smit.mimw.dto.FormUpdateData;
import com.smit.mimw.dto.FormUpdateRequest;
import com.smit.mimw.dto.ZaglavljeUpdateData;
import com.smit.mimw.dto.IsSuccessResponse;
import com.smit.mimw.dto.MimvDetaljItem;
import com.smit.mimw.dto.MimvProcessedItem;
import com.smit.mimw.dto.MimvZaglavljeItem;
import com.smit.mimw.dto.PreviewExistingResponse;
import com.smit.mimw.dto.TaxOffice;
import com.smit.mimw.dto.TaxOfficesResponse;
import com.smit.mimw.dto.TaxPayerType;
import com.smit.mimw.dto.TaxPayerTypeRequest;
import com.smit.mimw.dto.TaxPayerTypesResponse;
import com.smit.mimw.repository.As400Repository;
import com.smit.mimw.util.MimvXmlBuilder;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for all MIMV form operations.
 * Orchestrates AS400 reads, Pantheon writes, and form lifecycle.
 */
@Service
public class MimvFormService {

    private static final Logger log = LoggerFactory.getLogger(MimvFormService.class);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd"); //$NON-NLS-1$

    /**
     * Maps Pantheon taxpayer-type codes to MI-MV form-type codes (SIFRA_OBRASCA_PP).
     * MV02 -> 401: passenger cars / motorcycles (HFSFZGART: N, V)
     * MV03 -> 405: quads / ATVs               (HFSFZGART: A, G)
     */
    private static final Map<String, String> SIFOBR_BY_TAX_CODE = Map.of(
            "MV02", "401", //$NON-NLS-1$ //$NON-NLS-2$
            "MV03", "405"  //$NON-NLS-1$ //$NON-NLS-2$
    );

    private final As400Repository as400Repository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mimw.mail.from:noreply@smit.hr}") //$NON-NLS-1$
    private String mailFrom;

    public MimvFormService(As400Repository as400Repository)
    {
        this.as400Repository = as400Repository;
    }

    // -------------------------------------------------------------------------
    // Taxpayer types
    // -------------------------------------------------------------------------

    /**
     * Returns all MIMV taxpayer types from MIMV_ODABRANI_TIPOVI_OBVEZNIKA.
     * Descriptions are fixed per type code; selected flag reflects the BIT value in the table.
     */
    public TaxPayerTypesResponse getTaxPayerTypes(String mandatorId, String companyId) {
        log.info("Fetching taxpayer types for mandatorId={}, companyId={}", mandatorId, companyId); //$NON-NLS-1$
        List<String> selectedCodes = as400Repository.fetchOdabraniTipoviObveznika();
        log.info("selectedCodes: {}", selectedCodes); //$NON-NLS-1$
        List<TaxPayerType> types = List.of(
                TaxPayerType.builder().taxPayerCode("MV01").taxPayerDescription("PROIZVOĐAČ MOTORNIH VOZILA").selected(selectedCodes.contains("MV01")).build(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                TaxPayerType.builder().taxPayerCode("MV02").taxPayerDescription("TRGOVAC NOVIM MOT.VOZILIMA").selected(selectedCodes.contains("MV02")).build(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                TaxPayerType.builder().taxPayerCode("MV03").taxPayerDescription("REG.TRGOVAC RABLJENIM MOT.VOZ.").selected(selectedCodes.contains("MV03")).build() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        );
        return TaxPayerTypesResponse.builder()
                .taxPayerTypes(types)
                .build();
    }

    /**
     * Saves taxpayer type selection into MIMV_ODABRANI_TIPOVI_OBVEZNIKA.
     * The request list represents the selected codes; any code absent from the list is stored as 0.
     */
    public IsSuccessResponse saveTaxPayerType(String mandatorId, String companyId, List<TaxPayerTypeRequest> requests) {
        log.info("saveTaxPayerType called for mandatorId={}, companyId={}, count={}", mandatorId, companyId, requests.size()); //$NON-NLS-1$
        for (TaxPayerTypeRequest request : requests) {
            if (request.getTaxPayerCode() == null || request.getTaxPayerCode().isBlank()) {
                throw new IllegalArgumentException("taxPayerCode is required."); //$NON-NLS-1$
            }
        }
        String oib = as400Repository.fetchOibFromOdabraniTipoviObveznika();
        if (oib == null) {
            log.info("No existing row in MIMV_ODABRANI_TIPOVI_OBVEZNIKA, falling back to AS400 for OIB"); //$NON-NLS-1$
            oib = as400Repository.fetchOib();
        }
        boolean mv01 = requests.stream().anyMatch(r -> "MV01".equalsIgnoreCase(r.getTaxPayerCode()) && r.isSelected()); //$NON-NLS-1$
        boolean mv02 = requests.stream().anyMatch(r -> "MV02".equalsIgnoreCase(r.getTaxPayerCode()) && r.isSelected()); //$NON-NLS-1$
        boolean mv03 = requests.stream().anyMatch(r -> "MV03".equalsIgnoreCase(r.getTaxPayerCode()) && r.isSelected()); //$NON-NLS-1$
        as400Repository.upsertOdabraniTipoviObveznika(oib, mv01, mv02, mv03);
        log.info("Saved taxpayer type selection: MV01={}, MV02={}, MV03={}", mv01, mv02, mv03); //$NON-NLS-1$
        return IsSuccessResponse.builder().isSuccess(true).build();
    }

    // -------------------------------------------------------------------------
    // Form build — POST /mimv/form/build
    // -------------------------------------------------------------------------

    /**
     * Builds a MIMV form:
     * <ol>
     *   <li>Fetch OIB from AS400 KB0D1.HDB</li>
     *   <li>Resolve SIFRA_OBRASCA_PP from taxPayerCode mapping</li>
     *   <li>Delete any existing MIMV_DETALJ rows for this key (idempotency)</li>
     *   <li>Insert MIMV_DETALJ from AS400 HF tables via linked server</li>
     *   <li>Compute UKUP_IZNOS totals from newly inserted MIMV_DETALJ rows</li>
     *   <li>Delete any existing MIMV_ZAGLAVLJE row for this key (idempotency)</li>
     *   <li>Insert MIMV_ZAGLAVLJE from request body + computed totals</li>
     * </ol>
     */
    public FormBuildResponse buildForm(String mandatorId, String companyId, FormBuildRequest request) {
        log.info("buildForm: mandatorId={}, companyId={}, taxPayerCode={}, dateFrom={}, dateTo={}", //$NON-NLS-1$
                mandatorId, companyId, request.getTaxPayerCode(), request.getDateFrom(), request.getDateTo());

        // 1. Resolve OIB, company data, and form type code
        String oib = as400Repository.fetchOib();
        log.info("Fetched OIB from KB0D1.HDB: {}", oib != null ? "***" : "null"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        CompanyData companyData = as400Repository.fetchCompanyData();
        log.info("Fetched company data from KB0D1.ZBEN1"); //$NON-NLS-1$

        String sifobr = toSifobr(request.getTaxPayerCode());
        log.info("Resolved sifobr='{}' from taxPayerCode='{}'", sifobr, request.getTaxPayerCode()); //$NON-NLS-1$

        int seqNum     = request.getSequentialNumberInPeriod() != null ? request.getSequentialNumberInPeriod() : 1;
        int versionNum = request.getVersionNumber() != null ? request.getVersionNumber() : 1;

        int formDateInt = request.getFormDate() != null ? Integer.parseInt(request.getFormDate().format(YYYYMMDD)) : 0;
        String dateFrom = request.getDateFrom() != null ? request.getDateFrom().format(YYYYMMDD) : ""; //$NON-NLS-1$
        String dateTo   = request.getDateTo()   != null ? request.getDateTo().format(YYYYMMDD)   : ""; //$NON-NLS-1$

        // 2. Build composite identifier: oib-formDate-sifobr-seq-version
        String compositeId = String.format("%s-%s-%s-%02d-%03d", //$NON-NLS-1$
                oib != null ? oib : "", formDateInt, sifobr != null ? sifobr : "", seqNum, versionNum); //$NON-NLS-1$ //$NON-NLS-2$

        // 3. Delete existing detail rows first (FK child before parent)
        as400Repository.deleteMimvDetalj(oib, formDateInt, sifobr, seqNum, versionNum);

        // 4. Delete existing header row
        as400Repository.deleteMimvZaglavlje(oib, formDateInt, sifobr, seqNum, versionNum);

        // 5. Insert MIMV_ZAGLAVLJE with zero totals as placeholder — must exist before DETALJ due to FK_DETALJ_ZAGLAVLJE
        String actionCode = request.getActionCode() != null ? request.getActionCode() : "N"; //$NON-NLS-1$
        as400Repository.insertMimvZaglavlje(oib, formDateInt, sifobr, compositeId,
                request, companyData.getCompanyDescription(), companyData.getCompanySeat(),
                BigDecimal.ZERO, BigDecimal.ZERO);

        // 6. Insert MIMV_DETALJ from AS400 HF tables — FK satisfied by step 5
        as400Repository.insertMimvDetalj(mandatorId, oib, formDateInt, sifobr,
                seqNum, versionNum, dateFrom, dateTo, companyId);

        // 7. Compute totals from inserted detail rows and update header
        BigDecimal[] totals = as400Repository.sumMimvDetaljTotals(oib, formDateInt, sifobr, seqNum, versionNum);
        BigDecimal totalNew  = totals[0];
        BigDecimal totalUsed = totals[1];
        log.info("Computed totals: nova={}, rabljena={}", totalNew, totalUsed); //$NON-NLS-1$
        as400Repository.updateMimvZaglavljeTotals(oib, formDateInt, sifobr, seqNum, versionNum, totalNew, totalUsed);

        // 8. Fetch the just-inserted detail rows and selected taxpa/**/yer types for the response
        List<MimvDetaljItem> detalji = as400Repository.fetchMimvDetalj(oib, formDateInt, sifobr, seqNum, versionNum);
        log.info("Fetched {} MIMV_DETALJ rows for response", detalji.size()); //$NON-NLS-1$

        List<String> selectedTypes = as400Repository.fetchOdabraniTipoviObveznika(oib);
        log.info("Fetched {} selected taxpayer types for oib=***", selectedTypes.size()); //$NON-NLS-1$

        MimvZaglavljeItem zaglavlje = MimvZaglavljeItem.builder()
                .id(compositeId)
                .oibObveznika(oib)
                .datumPP(formDateInt)
                .sifraObrascaPP(sifobr)
                .redniBrojPP(seqNum)
                .redniBrojPPProm(versionNum)
                .actionCode(actionCode)
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .taxOfficeCode(request.getTaxOfficeCode())
                .taxOfficeDescription(request.getTaxOfficeDescription())
                .companyDescription(companyData.getCompanyDescription())
                .companySeat(companyData.getCompanySeat())
                .destinationEmail(request.getDestinationEmail())
                .responsiblePerson(request.getResponsiblePerson())
                .taxPayerCode(request.getTaxPayerCode())
                .taxNewVehiclesSum(totalNew)
                .taxUsedVehiclesSum(totalUsed)
                .taxTotalSum(totalNew.add(totalUsed))
                .selectedTaxPayerTypes(selectedTypes)
                .build();

        return FormBuildResponse.builder()
                .zaglavlje(zaglavlje)
                .detalji(detalji)
                .build();
    }

    // -------------------------------------------------------------------------
    // Preview existing — GET /mimv/preview/existing
    // -------------------------------------------------------------------------

    /**
     * Returns all previously submitted MIMV forms from MIMV_ZAGLAVLJE.
     */
    public PreviewExistingResponse getPreviewExisting(String mandatorId, String companyId) {
        log.info("getPreviewExisting: mandatorId={}, companyId={}", mandatorId, companyId); //$NON-NLS-1$
        List<MimvProcessedItem> processed = as400Repository.fetchAllMimvZaglavlje();
        log.info("Fetched {} MIMV_ZAGLAVLJE rows", processed.size()); //$NON-NLS-1$
        return PreviewExistingResponse.builder()
                .mimvProcessed(processed)
                .build();
    }

    // -------------------------------------------------------------------------
    // Form update — POST /mimv/form/build/update
    // -------------------------------------------------------------------------

    /**
     * Updates an existing MIMV form in Pantheon MSSQL.
     * OIB is fetched from AS400 (read-only, same as /form/build). All other data comes from the request.
     * <ol>
     *   <li>Fetch OIB from AS400 KB0D1.HDB (transparent to caller)</li>
     *   <li>Resolve SIFRA_OBRASCA_PP from taxPayerCode</li>
     *   <li>Delete existing MIMV_DETALJ rows (FK child before parent)</li>
     *   <li>Delete existing MIMV_ZAGLAVLJE row</li>
     *   <li>Re-insert MIMV_ZAGLAVLJE from request data (zero-total placeholder)</li>
     *   <li>Batch-insert MIMV_DETALJ from request detalji list</li>
     *   <li>Recompute totals and update MIMV_ZAGLAVLJE</li>
     * </ol>
     * Returns the same {@link FormBuildResponse} structure as POST /mimv/form/build.
     *
     * @param mandatorId mandator identifier from request header
     * @param companyId  company identifier from request header
     * @param request    update payload — form key + mutable zaglavlje fields + replacement detalji
     */
    public FormBuildResponse updateForm(String mandatorId, String companyId, FormUpdateRequest request)
    {
        FormUpdateData     d       = request.getData();
        ZaglavljeUpdateData z      = d.getZaglavlje();
        List<MimvDetaljItem> detalji = d.getDetalji();

        String oib        = as400Repository.fetchOib();
        String sifobr     = toSifobr(d.getTaxPayerCode());
        int    formDate   = d.getFormDate()                    != null ? d.getFormDate()                    : 0;
        int    seqNum     = d.getSequentialNumberInPeriod()    != null ? d.getSequentialNumberInPeriod()    : 1;
        int    versionNum = d.getVersionNumber()               != null ? d.getVersionNumber()               : 1;

        log.info("updateForm: formDate={}, taxPayerCode={}, sifobr={}, seq={}, ver={}", //$NON-NLS-1$
                formDate, d.getTaxPayerCode(), sifobr, seqNum, versionNum);

        String compositeId = String.format("%s-%s-%s-%02d-%03d", //$NON-NLS-1$
                oib != null ? oib : "", formDate, sifobr != null ? sifobr : "", seqNum, versionNum); //$NON-NLS-1$ //$NON-NLS-2$

        // Delete child rows first (FK constraint), then parent
        as400Repository.deleteMimvDetalj(oib, formDate, sifobr, seqNum, versionNum);
        as400Repository.deleteMimvZaglavlje(oib, formDate, sifobr, seqNum, versionNum);

        // Insert ZAGLAVLJE placeholder — must exist before DETALJ due to FK_DETALJ_ZAGLAVLJE
        as400Repository.insertMimvZaglavljeFromZaglavlje(oib, formDate, sifobr, seqNum, versionNum,
                compositeId, d.getTaxPayerCode(), z, BigDecimal.ZERO, BigDecimal.ZERO);

        // Insert DETALJ from request body
        as400Repository.insertMimvDetaljFromItems(detalji, oib, formDate, sifobr, seqNum, versionNum);

        // Recompute totals and update ZAGLAVLJE
        BigDecimal[] totals  = as400Repository.sumMimvDetaljTotals(oib, formDate, sifobr, seqNum, versionNum);
        BigDecimal totalNew  = totals[0];
        BigDecimal totalUsed = totals[1];
        log.info("updateForm totals: nova={}, rabljena={}", totalNew, totalUsed); //$NON-NLS-1$
        as400Repository.updateMimvZaglavljeTotals(oib, formDate, sifobr, seqNum, versionNum, totalNew, totalUsed);

        // Fetch persisted rows for response
        List<MimvDetaljItem> savedDetalji  = as400Repository.fetchMimvDetalj(oib, formDate, sifobr, seqNum, versionNum);
        List<String>         selectedTypes = as400Repository.fetchOdabraniTipoviObveznika(oib);

        MimvZaglavljeItem zaglavljeResponse = MimvZaglavljeItem.builder()
                .id(compositeId)
                .oibObveznika(oib)
                .datumPP(formDate)
                .sifraObrascaPP(sifobr)
                .redniBrojPP(seqNum)
                .redniBrojPPProm(versionNum)
                .actionCode(z.getActionCode() != null ? z.getActionCode() : "N") //$NON-NLS-1$
                .dateFrom(z.getDateFrom())
                .dateTo(z.getDateTo())
                .taxOfficeCode(z.getTaxOfficeCode())
                .taxOfficeDescription(z.getTaxOfficeDescription())
                .companyDescription(z.getCompanyDescription())
                .companySeat(z.getCompanySeat())
                .destinationEmail(z.getDestinationEmail())
                .responsiblePerson(z.getResponsiblePerson())
                .taxPayerCode(d.getTaxPayerCode())
                .taxNewVehiclesSum(totalNew)
                .taxUsedVehiclesSum(totalUsed)
                .taxTotalSum(totalNew.add(totalUsed))
                .selectedTaxPayerTypes(selectedTypes)
                .build();

        return FormBuildResponse.builder()
                .zaglavlje(zaglavljeResponse)
                .detalji(savedDetalji)
                .build();
    }

    // -------------------------------------------------------------------------
    // Brands
    // -------------------------------------------------------------------------

    /**
     * Returns all vehicle brands from AS400.
     */
    public BrandsResponse getBrands(String mandatorId, String companyId) {
        log.info("Fetching vehicle brands from AS400 for mandatorId={}", mandatorId); //$NON-NLS-1$
        List<Brand> brands = as400Repository.fetchBrands();
        return BrandsResponse.builder()
                .brands(brands)
                .build();
    }

    // -------------------------------------------------------------------------
    // Tax offices
    // -------------------------------------------------------------------------

    /**
     * Returns all customs/tax offices from AS400.
     */
    public TaxOfficesResponse getTaxOffices(String mandatorId, String companyId) {
        log.info("Fetching tax offices from AS400 for mandatorId={}", mandatorId); //$NON-NLS-1$
        List<TaxOffice> offices = as400Repository.fetchTaxOffices();
        return TaxOfficesResponse.builder()
                .taxOffices(offices)
                .build();
    }

    // -------------------------------------------------------------------------
    // Form confirm — POST /mimv/form/confirm
    // -------------------------------------------------------------------------

    /**
     * Validates the form data, generates an ET405AA XML document, and sends it as an email attachment
     * to the destinationEmail specified in the zaglavlje.
     * Throws {@link IllegalArgumentException} listing all missing fields when validation fails.
     *
     * @param mandatorId mandator identifier from request header
     * @param companyId  company identifier from request header
     * @param request    same payload as POST /mimv/form/build/update
     * @return the destination email address to which the XML was sent
     */
    public String confirmForm(String mandatorId, String companyId, FormUpdateRequest request)
    {
        FormUpdateData      data    = request.getData();
        ZaglavljeUpdateData z       = data != null ? data.getZaglavlje() : null;
        List<MimvDetaljItem> detalji = data != null ? data.getDetalji() : null;

        List<String> zaglavljeErrors = collectZaglavljeErrors(z, data);
        List<String> detaljiErrors   = collectDetaljiErrors(detalji);

        if (!zaglavljeErrors.isEmpty() || !detaljiErrors.isEmpty())
        {
            StringBuilder msg = new StringBuilder();
            if (!zaglavljeErrors.isEmpty())
            {
                msg.append("U zaglavlju nedostaje: ").append(String.join(", ", zaglavljeErrors)); //$NON-NLS-1$
            }
            if (!detaljiErrors.isEmpty())
            {
                if (msg.length() > 0)
                {
                    msg.append("; "); //$NON-NLS-1$
                }
                msg.append("U detaljima na nekim vozilima nedostaje: ").append(String.join(", ", detaljiErrors)); //$NON-NLS-1$
            }
            log.warn("confirmForm validation failed: {}", msg); //$NON-NLS-1$
            throw new IllegalArgumentException(msg.toString());
        }

        if (mailSender == null)
        {
            log.error("Mail sender not configured — set MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD env vars."); //$NON-NLS-1$
            throw new IllegalStateException("Mail sender not configured."); //$NON-NLS-1$
        }

        String oib = as400Repository.fetchOib();

        BigDecimal totalNew  = BigDecimal.ZERO;
        BigDecimal totalUsed = BigDecimal.ZERO;
        for (MimvDetaljItem item : detalji)
        {
            BigDecimal iznos = item.getObracunatiIznosPP() != null ? item.getObracunatiIznosPP() : BigDecimal.ZERO;
            if ("R".equalsIgnoreCase(item.getStatusVozila())) //$NON-NLS-1$
            {
                totalUsed = totalUsed.add(iznos);
            }
            else
            {
                totalNew = totalNew.add(iznos);
            }
        }
        log.info("confirmForm totals: nova={}, rabljena={}", totalNew, totalUsed); //$NON-NLS-1$

        String xmlContent = MimvXmlBuilder.build(oib, data, totalNew, totalUsed);

        String sifobr   = "MV03".equalsIgnoreCase(data.getTaxPayerCode()) ? "405" : "401"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String filename = "ET405AA_" + sifobr + ".xml"; //$NON-NLS-1$ //$NON-NLS-2$
        String email    = z.getDestinationEmail();

        sendXmlEmail(email, xmlContent, filename, z);

        log.info("confirmForm: XML sent to {}", email); //$NON-NLS-1$
        return email;
    }

    private List<String> collectZaglavljeErrors(ZaglavljeUpdateData z, FormUpdateData data)
    {
        List<String> errors = new ArrayList<>();
        if (isBlank(z != null ? z.getDestinationEmail()    : null)) errors.add("email adresa"); //$NON-NLS-1$
        if (isBlank(z != null ? z.getTaxOfficeCode()       : null)) errors.add("šifra carinskog ureda"); //$NON-NLS-1$
        if (isBlank(z != null ? z.getTaxOfficeDescription(): null)) errors.add("opis carinskog ureda"); //$NON-NLS-1$
        if (isBlank(z != null ? z.getCompanyDescription()  : null)) errors.add("naziv obveznika"); //$NON-NLS-1$
        if (isBlank(z != null ? z.getCompanySeat()         : null)) errors.add("sjedište obveznika"); //$NON-NLS-1$
        if (isBlank(data != null ? data.getTaxPayerCode()  : null)) errors.add("vrsta obveznika (taxPayerCode)"); //$NON-NLS-1$
        if (z == null || z.getDateFrom() == null)                    errors.add("datum od (dateFrom)"); //$NON-NLS-1$
        if (z == null || z.getDateTo()   == null)                    errors.add("datum do (dateTo)"); //$NON-NLS-1$
        return errors;
    }

    private List<String> collectDetaljiErrors(List<MimvDetaljItem> detalji)
    {
        if (detalji == null || detalji.isEmpty())
        {
            return List.of("lista vozila je prazna"); //$NON-NLS-1$
        }
        Set<String> missing = new LinkedHashSet<>();
        for (MimvDetaljItem item : detalji)
        {
            if (item.getObracunatiIznosPP()    == null)                          missing.add("obračunati iznos PP"); //$NON-NLS-1$
            if (item.getDatumIzdavanjaRacuna() == null || item.getDatumIzdavanjaRacuna() == 0) missing.add("datum izdavanja računa"); //$NON-NLS-1$
            if (isBlank(item.getBrojRacuna()))                                   missing.add("broj računa"); //$NON-NLS-1$
            if (isBlank(item.getOib()))                                          missing.add("OIB kupca"); //$NON-NLS-1$
            if (isBlank(item.getPorezniObveznik()))                              missing.add("porezni obveznik"); //$NON-NLS-1$
            if (item.getProdajnaCijena()        == null)                         missing.add("prodajna cijena"); //$NON-NLS-1$
            if (item.getProsjEmisijaCO2()       == null)                         missing.add("prosječna emisija CO2"); //$NON-NLS-1$
            if (isBlank(item.getVinOznaka()))                                    missing.add("VIN oznaka"); //$NON-NLS-1$
            if (isBlank(item.getMarkaVozila()))                                  missing.add("marka vozila"); //$NON-NLS-1$
        }
        return new ArrayList<>(missing);
    }

    private void sendXmlEmail(String toEmail, String xmlContent, String filename, ZaglavljeUpdateData z)
    {
        try
        {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8"); //$NON-NLS-1$
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            String dateRange = (z.getDateFrom() != null ? z.getDateFrom().toString() : "") //$NON-NLS-1$
                    + " / " //$NON-NLS-1$
                    + (z.getDateTo() != null ? z.getDateTo().toString() : ""); //$NON-NLS-1$
            helper.setSubject("MI-MV Obrazac - " + dateRange); //$NON-NLS-1$
            helper.setText("U prilogu se nalazi MI-MV obrazac u XML formatu."); //$NON-NLS-1$
            helper.addAttachment(filename, new ByteArrayResource(xmlContent.getBytes(StandardCharsets.UTF_8)));
            mailSender.send(message);
        }
        catch (Exception e)
        {
            log.error("Failed to send XML email to {}: {}", toEmail, e.getMessage(), e); //$NON-NLS-1$
            throw new RuntimeException("Slanje emaila nije uspjelo: " + e.getMessage(), e); //$NON-NLS-1$
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String toSifobr(String taxPayerCode)
    {
        if (taxPayerCode == null)
        {
            return null;
        }
        return SIFOBR_BY_TAX_CODE.get(taxPayerCode.toUpperCase());
    }

    private static boolean isBlank(String s)
    {
        return s == null || s.isBlank();
    }
}
