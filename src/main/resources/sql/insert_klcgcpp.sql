-- ============================================================
-- INSERT INTO IVAS<SIFPOD>.KLCGCPP  (MVMZP Detalj)
-- ============================================================
-- Placeholders (replaced at runtime):
--   <SIFPOD>    – mandatorId from request header  (e.g. 0000B0)
--   <OIB>       – OIB obveznika                   (e.g. 30985203273)
--   <DATUM>     – DatumPP as integer YYYYMMDD      (e.g. 20260401)
--   <SIFOBR>    – SifraObrascaPP                  (e.g. 401 or 405)
--   <RBR>       – RedniBrojPP                     (e.g. 1)
--   <RBRPROM>   – RedniBrojPPPromjena              (e.g. 0)
--   <ODDATUMA>  – Period start as integer YYYYMMDD (e.g. 20260101)  → dateFrom from request body
--   <DODATUMA>  – Period end   as integer YYYYMMDD (e.g. 20260331)  → dateTo   from request body
--   <BRANCH>    – Pantheon company branch number   (e.g. 1)         → companyId from request header
-- ============================================================

insert into ivas<SIFPOD>.klcgcpp
select '<SIFPOD>' pod, '<OIB>', <DATUM> datum_pod, '<SIFOBR>' sifobr,
<RBR> rbr, <RBRPROM> rbr_prom, hfskey sif_vozila, trim(vrsta_vozila_3) vrstavoz,
ifnull(CHYSAA, '') marka, upper(trim(marka_vozila_4)) markaopis,
trim(left(naziv, nazlen)) || ', ' || dodatak Tip_varij_i_trg_naz,
trim(VIN_6) VIN_6, trim(Vrsta_goriva_7) Vrsta_goriva_7, c02_8,
-- case when '<SIFPOD>' = '0000F7' then razina_emisije_ispusnih_plinova_9F7 else razina_emisije_ispusnih_plinova_9 end razina,
razina_emisije_ispusnih_plinova_9 as razina,
case when '<SIFPOD>' = '0000F7' then radni_obujam_motora_10F7 else radni_obujam_motora_10 end obujam,
trim(broj_potvrde_o_sukladnosti_11) broj_potvrde_o_sukladnosti_11, Porezna_osnovica_kn_12, porezna_stopa_13,
iznos_posebnog_poreza_14, oslobodenje_15, plug_in_16, kamper_17, porezni_obveznik_18,
case when OIB_19 is not null then left(oib_19, 11) else '' end OIB_19,
Broj_racuna_20, Datum_racuna_21, Iznos_uplacenog_posebnog_poreza_22, Datum_uplacenog_posebnog_poreza_23
from
(select
    hfskey,
    '1' as vrsta_vozila_3,
    martxt as marka_vozila_4,
    hfptxt naziv,
    trim(case when upper(hfptxt) like '%AUTOM%' then 'automatski' else 'ručni' end)
        || ', ' ||
    trim(case when PCDLACKART = 'M' then 'metalik' else 'obična' end) dodatak,
    50 - length(
        trim(case when upper(hfptxt) like '%AUTOM%' then 'automatski' else 'ručni' end)
        || ', ' ||
        trim(case when PCDLACKART = 'M' then 'metalik' else 'obična' end)
    ) - 2 nazlen,
    substr(HFSFGST, 4, 17) as VIN_6,
    case when upper(hfstrb) = 'N' then 'D' else hfstrb end as Vrsta_goriva_7,
    HFSCO2 as c02_8,
    -- left(HFSSCHL3, 5) as razina_emisije_ispusnih_plinova_9F7,
    -- '' as razina_emisije_ispusnih_plinova_9,
    case when HFBDATFTR >= 20170101
         then ''
         else case when upper(hfstrb) = 'N' then 'VI' else '' end
    end as razina_emisije_ispusnih_plinova_9,
    HFSHUBR radni_obujam_motora_10F7,
    0       radni_obujam_motora_10,
    HFSBEMERK as broj_potvrde_o_sukladnosti_11,
    HFKHRTBAS as Porezna_osnovica_kn_12,
    HFKHRBASPR + HFKHRCO2PR as porezna_stopa_13,
    HFKHRTVAL as iznos_posebnog_poreza_14,
    '' as oslobodenje_15,
    0  as plug_in_16,
    '' as kamper_17,
    case when hfbrpnm1 = '' then HFKKFNM1   else hfbrpnm1   end as porezni_obveznik_18,
    case when hfbrpnm1 = '' then HFKKFUIDNR else hfbrpuidnr end as OIB_19,
    trim(char(HFBBLFA))
        || '/' || trim(char(case when t1.aenbet > 0 then t1.aenbet else t1.anlbet end))
        || '/6' as Broj_racuna_20,
    HFBDATFTR Datum_racuna_21,
    case when (HFKBLZOLL = ' ' or isnumericdec(HFKBLZOLL) = 0)
         then HFKHRTVAL
         else dec(replace(trim(HFKBLZOLL), ',', '.'), 13, 2)
    end as Iznos_uplacenog_posebnog_poreza_22,
    case when HFKDATZOLL = 0
         then int(datefmt(current date, 'yyyyMMdd'))
         else int(trim(char(HFKDATZOLL)))
    end as Datum_uplacenog_posebnog_poreza_23,
    CHYSAA
from
    hfs hfs
    left join hfk on HFSKEY = HFKKEY
    left join perv on HFKVK = pebper
    join hfb t1 on HFSKEY = HFBKEY
    left join hbj t2 on
        HFBKEY = HBJHFSKEY1 and
        HFBBLFA = HBJBNR and
        HBJBDAT = HFBDATFTR and
        HBJAKT in ('FA', 'FG')
    left join ads on (case when hfbrpnm1 = '' then HFKKFKDNR else hfbrpkdnr end) = ADSKEY
    left join hfp on HFSKEY = HFPHFSNR
    join mar on marmar = HFSMAR
    left join pcd on hfsfar = pcdpcd and hfsmar = pcdmar
    left join ivasxt.klchcpp on upper(martxt) = CHL6AQ
where
    HFBDATFTR between <ODDATUMA> and <DODATUMA>
    and (
        case when HFKDATZOLL <> 0 then HFKDATZOLL end between <ODDATUMA> and <DODATUMA>
        or HFKDATZOLL = 0
        or HFKDATZOLL >= <DODATUMA>
    )
    and HFSVKSTS = 'F'
    and HFPPART = 'F'
    and HFKHRTKZ <> ''
    and (HFKHRTVAL <> 0 or (HBJHRTVAL is not null and HBJHRTVAL <> 0))
    and case when HBJHRTBAS is null then HFKHRTBAS else HBJHRTBAS end > 0
    and (
        HFSFZGART in (
            select tipvoz from (
                select 'N' tipvoz from sysibm.sysdummy1
                union all
                select 'V' tipvoz from sysibm.sysdummy1
            ) t
            where exists (
                select * from ivasdet
                where aupgm = 'KMDPDFR'
                and locate('MV02', auhhap) > 0
            )
        )
        or
        HFSFZGART in (
            select tipvoz from (
                select 'A' tipvoz from sysibm.sysdummy1
                union all
                select 'G' tipvoz from sysibm.sysdummy1
            ) t
            where exists (
                select * from ivasdet
                where aupgm = 'KMDPDFR'
                and locate('MV03', auhhap) > 0
            )
        )
    )
    and case when t1.aenbet > 0 then t1.aenbet else t1.anlbet end = <BRANCH>
    and (char(hfskey) not in ('') or '' in (''))
order by pesname, HFSMAR, HFBDATFTR
) t

