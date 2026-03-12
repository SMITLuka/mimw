package com.smit.mimw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stavka vozila unutar MI-MV obrasca (redak tablice).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoziloStavka {

    /** 1. Status vozila (novo / rabljeno) */
    private String statusVozila;

    /** 2. Vrsta vozila */
    private String vrstaVozila;

    /** 3. Marka vozila */
    private String markaVozila;

    /** 4. Trgovački naziv, razina opreme, mjenjač i boja (obična/metalik) */
    private String trgovackiNaziv;

    /** 5. VIN oznaka (broj šasije) vozila */
    private String vinOznaka;

    /** 6. Vrsta goriva */
    private String vrstaGoriva;

    /** 7. Datum prve registracije */
    private LocalDate datumPrveRegistracije;

    /** 8. Prosječna emisija CO₂ (g/km) */
    private BigDecimal prosjecnaEmisijaCO2;

    /** 9. Razina emisije ispušnih plinova */
    private String razinaEmisijeIspusnihPlinova;

    /** 10. Radni obujam motora (cm³) */
    private Integer radniObujamMotora;

    /** 11. Snaga motora (kW) */
    private BigDecimal snagaMotora;

    /** 12. Prodajna cijena (kn) */
    private BigDecimal prodajnaCijena;

    /** 13. Broj prijeđenih kilometara (km) */
    private Integer brojPrijedjenihKilometara;

    /** 14. Kamper (%) */
    private BigDecimal kamperPostotak;

    /** 15. Plug-in hibridno električno vozilo (%) */
    private BigDecimal plugInHibridPostotak;

    /** 16. Vozilo s 7+1 sjedala (%) */
    private BigDecimal vozilo7Plus1Postotak;

    /** 17. Vozilo s 8+1 sjedala (%) */
    private BigDecimal vozilo8Plus1Postotak;

    /** 18. Testno vozilo koje je izneseno/izvezeno/uništeno (%) */
    private BigDecimal testnoVoziloPostotak;

    /** 19. Deprecijacija (%) */
    private BigDecimal deprecijacijaPostotak;

    /** 20. Porezni obveznik */
    private String porezniObveznik;

    /** 21. OIB (poreznog obveznika) */
    private String oibPoreznog;

    /** 22. Broj računa */
    private String brojRacuna;

    /** 23. Datum izdavanja računa */
    private LocalDate datumIzdavanjaRacuna;

    /** 24. Obračunati iznos posebnog poreza (kn) */
    private BigDecimal obracunatiIznosPosebnogPoreza;
}

