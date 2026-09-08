# Material Property Audit

This table records the basis used for the material records called out by the
repository audit.  Values are SI.  A dash means the cited source did not
publish a defensible scalar value for that property.

These are preliminary-analysis properties, not flight-certified allowables.
Metal strength depends on product form, thickness, heat treatment, and lot.
Wood, laminates, and printed polymers are orthotropic, so orientation must be
checked against the component and load direction.

| Database material | Density kg/m3 | E GPa | G GPa | Tensile limit MPa | Compression MPa | Poisson | Property basis |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Polypropylene, bulk | 946 | 1.50 | 0.517 | 28 | 30.3 | 0.45 | Legacy library density; INEOS blow-moulding PP stiffness/yield, Densetec copolymer PP compression, derived G |
| Aluminum (6061-T6 basis) | 2700 | 68.3 | 25.68 | 241 | — | 0.33 | Kaiser 6061-T6 sheet/plate minimum yield; generic `Aluminum` is a conservative compatibility alias |
| Aluminum 6061-T6 | 2700 | 68.3 | 25.68 | 241 | — | 0.33 | Kaiser minimum yield, not the higher 276 MPa typical value |
| Aluminum 7075-T6 | 2810 | 71.7 | 26.95 | 434 | — | 0.33 | Kaiser minimum for bare T6 sheet at 0.008–0.011 in; thicker product must be checked separately |
| Steel 4130 normalized | 7850 | 205 | 80.0 | 460 | — | 0.29 | ASM record for 870 °C normalized, air-cooled, 13 mm round |
| Stainless steel 304 annealed | 7900 | 200 | 76.92 | 210 | — | 0.30 | Outokumpu Core 304 hot-rolled/plate minimum proof strength; G derived from E and Poisson ratio |
| Carbon fiber (fabric compatibility alias) | 1600 | 70 | 5.5 | 800 | 700 | 0.05 | Same documented Hexcel fabric basis as the qualified record |
| Carbon fiber/epoxy fabric, about 60% fibre volume | 1600 | 70 | 5.5 | 800 | 700 | 0.05 | Hexcel typical high-strength carbon fabric laminate; not a UD or arbitrary tube layup |
| Fiberglass (G10/FR4 compatibility alias) | 1800 | 14.37 | 6.20 | 262 | 448 | 0.159 | Same documented G10/FR4 basis as the qualified record |
| Fiberglass G10/FR4 laminate, in-plane | 1800 | 14.37 | 6.20 | 262 | 448 | 0.159 | NASA FR4 in-plane elastic constants; conservative crosswise tensile and Atlas G10 compression values |
| S2Fiberglass (legacy compatibility record) | 2000 | 28.5 | 3.79 | 551 | 561 | 0.138 | NCAMP room-temperature-dry measured means for MTM45-1/6781 S2-glass fabric laminate; saved legacy density retained |
| Balsa, 170 kg/m3 longitudinal basis | 170 | 3.0 | 0.111 | 14 | 7 | — | Density-conditioned approximation; G uses the USDA `G_LT/E_L` ratio; no single Poisson ratio is valid |
| Basswood, American, 12% moisture | 500 | 10.1 | 0.331 | 60 | 32.6 | — | USDA modulus of elasticity, modulus of rupture used as a scalar bending limit, and compression parallel to grain; legacy library density/shear retained |
| PLA (Verbatim FDM, 100% infill, direction 1) | 1240 | 2.71 | 1.24 | 50 | — | 0.328 | Tested Verbatim PLA; compression conservatively falls back to the tensile limit |
| Birch, yellow, about 12% moisture | 670 | 13.9 | 0.945 | 114 | 56.3 | — | USDA longitudinal E, modulus of rupture, compression parallel to grain, and G_LT/E_L ratio |
| Blue Tube / vulcanized fibre, conservative CD | 1200 | 5.516 | 2.529 | 62.05 | 241.3 | 0.227 | Supplier cross-direction strength/E with Dynal vulcanized-fibre G12 and nu21 |
| Cardboard paper tube | 680 | 1.5 | 0.4 | 8 | 11.05 | — | Conservative paper-tube literature values and a matching-density rocket tube compression test |

The legacy database name `PLA - 100% infill` remains as a compatibility
alias at its original 1250 kg/m3 density. It uses the qualified Verbatim
direction-1 mechanical basis so older `.ork` files receive complete values
without changing their mass model.

## Values rejected or corrected

- Stainless 304 `G = 86 GPa` was inconsistent with its E/Poisson pair.  The
  database now uses a self-consistent 76.92 GPa.
- PLA `E = 3.5 GPa`, `G = 2.4 GPa`, `nu = 0.36` was not an internally
  consistent isotropic set and did not describe a documented print direction.
  It was replaced with one experimentally tested 100%-infill directional set.
- A single balsa Poisson ratio was removed.  USDA publishes a direction matrix,
  not one scalar value.
- The previous carbon record said “UD, 50% fibre volume” while its E value
  matched a fabric/quasi-isotropic approximation.  It is now an explicit
  Hexcel fabric-laminate property basis with the loading direction documented.
- The previous G10 `E = 20 GPa`, `G = 4.14 GPa` pair mixed undocumented
  approximations.  The replacement identifies an in-plane FR4/G10 basis.
- Blue Tube density `1300 kg/m3` was replaced by the supplier's `1.20 g/cc`;
  its fibre-on-fibre friction coefficient was reduced from the unsupported
  0.40 estimate to the supplier's 0.16 nominal value.
- Flat elastic-cord line densities for 19 mm and 25 mm widths had missing
  decimal-place zeros (`0.0012`, `0.0016 kg/m`).  They are now `0.012` and
  `0.016 kg/m`, restoring monotonic mass with width.

## Calculator wiring

- Structures receives the exact material object assigned to each component.
  It uses E/G for stiffness, the lower tensile/compressive limit for mixed
  stress, and the compressive limit for Johnson column buckling.
- The ejection-charge calculator now receives the selected bay and coupler
  material objects.  Their E, Poisson ratio, and tensile limit override the old
  airframe-category estimates.  The category is retained for pairwise friction
  and as an explicit fallback for incomplete legacy/custom materials.
- Density and structural properties survive material-string storage, `.ork`
  save/load, preset XML, and Java component-preset serialization.
- Legacy `.ork` records with no structural fields are upgraded from a
  same-named database material when density is within 15%.  The saved density
  is retained, so opening an old design does not change its mass model.
- Direction-dependent and fallback cases produce cautions.  Ejection-charge
  results still require a representative ground test.

## Sources

- Kaiser Aluminum, *Sheet & Plate Product Information*:
  https://online.kaiseraluminum.com/depot/PublicProductInformation/Document/1010/Kaiser_Aluminum_Sheet___Plate.pdf
- Outokumpu, *Core range datasheet*:
  https://www.outokumpu.com/en/products/product-ranges/-/media/files/products/core/outokumpu-core-range-datasheet.pdf
- ASM/MatWeb, *AISI 4130 normalized at 870 °C*:
  https://asm.matweb.com/search/SpecificMaterial.asp?bassnum=M4130B
- Hexcel, *Prepreg Technology*:
  https://www.hexcel.com/wp-content/uploads/2026/01/Prepreg_Technology-2.pdf
- NASA NTRS, FR4 linear-elastic properties:
  https://ntrs.nasa.gov/api/citations/19910006819/downloads/19910006819.pdf
- NCAMP MTM45-1/6781 S2-glass qualification report:
  https://www.wichita.edu/industry_and_defense/NIAR/Research/cytec-mtm45-1/Style-6781-S2-Glass-2.pdf
- JPS Composite Materials data book (the workbook's S-glass constituent source):
  https://jpscm.com/resources/data-book/
- Atlas Fibre, G10 data:
  https://www.atlasfibre.com/material/g-10/
- USDA Forest Products Laboratory, *Wood Handbook*:
  https://www.fpl.fs.usda.gov/documnts/fplgtr/fplgtr282/fpl_gtr282.pdf
- Experimentally characterized FDM PLA:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC7660314/
- Verbatim PLA product density:
  https://www.verbatim-europe.com/en/3d-printing-filaments/products/verbatim-pla-filament-175mm-1kg-black-55318
- Always Ready Rocketry / Vulcanex, vulcanized-fibre engineering data:
  https://alwaysreadyrocketry.com/wp-content/uploads/2020/01/Blue-Tube-Engineering-Data.pdf
- INEOS, Eltex TUB433-NA00 blow-moulding polypropylene:
  https://www.ineos.com/Show-Document/?BU=INEOS+O+%26+P+Europe&DocumentType=Technical+Data+Sheet&Grade=433-NA00
- Polymer Industries, Densetec copolymer polypropylene:
  https://polymerindustries.com/wp-content/uploads/2018/03/Typical-Properties-PP-Copolymer.pdf
- Delaware DNREC, HDPE/PP design properties (Poisson ratio):
  https://documents.dnrec.delaware.gov/Admin/Documents/dnrec-hearings/2020-P-W-0014/operations/2012-Operation-and-Maintenance-Manual-Part-1.pdf
- DYNAmore proceedings, experimental vulcanized-fibre model:
  https://www.dynamore.de/en/training/conferences/past/13th-european-ls-dyna-conference-2021/2021-eu-proceedings.pdf
- Experimental paper-tube mechanical properties:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC12430042/
- UC Denver, rocket body-tube compression testing:
  https://engineering.ucdenver.edu/docs/librariesprovider29/college-of-engineering-and-applied-science/sp2020-capstone/mech4-report.pdf

