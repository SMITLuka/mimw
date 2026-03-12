package com.smit.mimw.service;

import com.smit.mimw.dto.MimvObrazac;
import com.smit.mimw.dto.VoziloStavka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Servis za rad s MI-MV obrascem.
 * <p>
 * TODO: Zamijeniti dummy implementaciju s AS400 DB2 konekcijom.
 */
@Service
public class MimvObrazacService {

    private static final Logger log = LoggerFactory.getLogger(MimvObrazacService.class);

    /**
     * Dohvaća MI-MV obrazac s dummy podacima.
     * <p>
     * TODO: Implementirati dohvat iz AS400 DB2 baze.
     *       Primjer:
     *       - jdbcTemplate.query("SELECT ... FROM MIMV_OBRAZAC WHERE ...", ...)
     *       - ili koristiti JPA repository
     */
    public MimvObrazac dohvatiObrazac(Integer godina, Integer mjesec) {
        log.info("Dohvaćam MI-MV obrazac za {}/{}", mjesec, godina);

        // TODO: Zamijeniti s pozivom prema AS400 DB2 bazi
        // Primjer: return mimvRepository.findByGodinaAndMjesec(godina, mjesec);

        return kreirajDummyObrazac(godina, mjesec);
    }

    /**
     * Sprema MI-MV obrazac.
     * <p>
     * TODO: Implementirati spremanje na AS400 DB2 bazu.
     *       Primjer:
     *       - jdbcTemplate.update("INSERT INTO MIMV_OBRAZAC ...", ...)
     *       - ili koristiti JPA repository
     */
    public MimvObrazac spremiObrazac(MimvObrazac obrazac) {
        log.info("Spremam MI-MV obrazac za {}, OIB: {}", obrazac.getNaziv(), obrazac.getOib());

        // --- Validacija ---
        validirajObrazac(obrazac);

        // --- Izračun sumarnih iznosa ---
        izracunajSumarne(obrazac);

        // TODO: Zamijeniti s pozivom prema AS400 DB2 bazi
        // Primjer: mimvRepository.save(obrazac);
        // Primjer JDBC:
        //   jdbcTemplate.update(
        //       "INSERT INTO MIMV_HEADER (NAZIV, SJEDISTE, OIB, ...) VALUES (?, ?, ?, ...)",
        //       obrazac.getNaziv(), obrazac.getSjediste(), obrazac.getOib(), ...
        //   );
        //   for (VoziloStavka stavka : obrazac.getStavke()) {
        //       jdbcTemplate.update(
        //           "INSERT INTO MIMV_STAVKA (VIN, MARKA, ...) VALUES (?, ?, ...)",
        //           stavka.getVinOznaka(), stavka.getMarkaVozila(), ...
        //       );
        //   }

        log.info("Obrazac uspješno spremljen (dummy). Ukupno stavki: {}",
                obrazac.getStavke() != null ? obrazac.getStavke().size() : 0);

        return obrazac;
    }

    // ===== Privatne metode =====

    private void validirajObrazac(MimvObrazac obrazac) {
        if (obrazac.getOib() == null || obrazac.getOib().isBlank()) {
            throw new IllegalArgumentException("OIB je obavezan.");
        }
        if (obrazac.getNaziv() == null || obrazac.getNaziv().isBlank()) {
            throw new IllegalArgumentException("Naziv tvrtke je obavezan.");
        }
        if (obrazac.getGodina() == null) {
            throw new IllegalArgumentException("Godina je obavezna.");
        }
        if (obrazac.getStavke() == null || obrazac.getStavke().isEmpty()) {
            throw new IllegalArgumentException("Obrazac mora sadržavati barem jednu stavku vozila.");
        }
    }

    private void izracunajSumarne(MimvObrazac obrazac) {
        BigDecimal novaVozila = BigDecimal.ZERO;
        BigDecimal rablenaVozila = BigDecimal.ZERO;

        for (VoziloStavka stavka : obrazac.getStavke()) {
            BigDecimal iznos = stavka.getObracunatiIznosPosebnogPoreza();
            if (iznos == null) {
                iznos = BigDecimal.ZERO;
            }
            if ("NOVO".equalsIgnoreCase(stavka.getStatusVozila())) {
                novaVozila = novaVozila.add(iznos);
            } else {
                rablenaVozila = rablenaVozila.add(iznos);
            }
        }

        obrazac.setUkupnoNovaVozila(novaVozila);
        obrazac.setUkupnoRablenaVozila(rablenaVozila);
        obrazac.setUkupnoSveVozila(novaVozila.add(rablenaVozila));
    }

    private MimvObrazac kreirajDummyObrazac(Integer godina, Integer mjesec) {

        VoziloStavka stavka1 = VoziloStavka.builder()
                .statusVozila("NOVO")
                .vrstaVozila("M1")
                .markaVozila("Volkswagen")
                .trgovackiNaziv("Golf 8 Style 1.5 TSI, automatski, srebrna metalik")
                .vinOznaka("WVWZZZ1KZMP012345")
                .vrstaGoriva("Benzin")
                .datumPrveRegistracije(LocalDate.of(2026, mjesec != null ? mjesec : 1, 15))
                .prosjecnaEmisijaCO2(new BigDecimal("126"))
                .razinaEmisijeIspusnihPlinova("Euro 6d")
                .radniObujamMotora(1498)
                .snagaMotora(new BigDecimal("110"))
                .prodajnaCijena(new BigDecimal("32000.00"))
                .brojPrijedjenihKilometara(0)
                .kamperPostotak(BigDecimal.ZERO)
                .plugInHibridPostotak(BigDecimal.ZERO)
                .vozilo7Plus1Postotak(BigDecimal.ZERO)
                .vozilo8Plus1Postotak(BigDecimal.ZERO)
                .testnoVoziloPostotak(BigDecimal.ZERO)
                .deprecijacijaPostotak(BigDecimal.ZERO)
                .porezniObveznik("Ivica Horvat")
                .oibPoreznog("12345678901")
                .brojRacuna("R-2026-001")
                .datumIzdavanjaRacuna(LocalDate.of(2026, mjesec != null ? mjesec : 1, 20))
                .obracunatiIznosPosebnogPoreza(new BigDecimal("4500.00"))
                .build();

        VoziloStavka stavka2 = VoziloStavka.builder()
                .statusVozila("RABLJENO")
                .vrstaVozila("M1")
                .markaVozila("BMW")
                .trgovackiNaziv("320d xDrive, automatski, crna obična")
                .vinOznaka("WBA8E1C05JA987654")
                .vrstaGoriva("Dizel")
                .datumPrveRegistracije(LocalDate.of(2022, 6, 10))
                .prosjecnaEmisijaCO2(new BigDecimal("134"))
                .razinaEmisijeIspusnihPlinova("Euro 6d")
                .radniObujamMotora(1995)
                .snagaMotora(new BigDecimal("140"))
                .prodajnaCijena(new BigDecimal("28000.00"))
                .brojPrijedjenihKilometara(85000)
                .kamperPostotak(BigDecimal.ZERO)
                .plugInHibridPostotak(BigDecimal.ZERO)
                .vozilo7Plus1Postotak(BigDecimal.ZERO)
                .vozilo8Plus1Postotak(BigDecimal.ZERO)
                .testnoVoziloPostotak(BigDecimal.ZERO)
                .deprecijacijaPostotak(new BigDecimal("35"))
                .porezniObveznik("Ana Kovačević")
                .oibPoreznog("98765432109")
                .brojRacuna("R-2026-002")
                .datumIzdavanjaRacuna(LocalDate.of(2026, mjesec != null ? mjesec : 1, 22))
                .obracunatiIznosPosebnogPoreza(new BigDecimal("2100.00"))
                .build();

        return MimvObrazac.builder()
                .naziv("Auto Hrvatska d.o.o.")
                .sjediste("Zagreb, Ulica grada Vukovara 274")
                .oib("11223344556")
                .carinskiUred("Carinski ured Zagreb")
                .periodOd(mjesec != null ? mjesec : 1)
                .periodDo(mjesec != null ? mjesec : 1)
                .godina(godina != null ? godina : 2026)
                .tipObveznika("TRGOVAC")
                .stavke(List.of(stavka1, stavka2))
                .ukupnoNovaVozila(new BigDecimal("4500.00"))
                .ukupnoRablenaVozila(new BigDecimal("2100.00"))
                .ukupnoSveVozila(new BigDecimal("6600.00"))
                .datumPotvrde("2026-03-01")
                .odgovornaOsoba("Marko Marić")
                .build();
    }
}

