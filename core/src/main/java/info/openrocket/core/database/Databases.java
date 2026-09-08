package info.openrocket.core.database;

import info.openrocket.core.material.MaterialGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.openrocket.core.l10n.Translator;
import info.openrocket.core.material.Material;
import info.openrocket.core.material.Material.Type;
import info.openrocket.core.material.MaterialStorage;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.MathUtil;

/**
 * A class that contains single instances of {@link Database} for specific purposes.
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class Databases {
	private static final Logger log = LoggerFactory.getLogger(Databases.class);
	private static final Translator trans = Application.getTranslator();
	
	
	/* Static implementations of specific databases: */
	
	/**
	 * A database of bulk materials (with bulk densities).
	 */
	public static final Database<Material> BULK_MATERIAL = new Database<>();
	/**
	 * A database of surface materials (with surface densities).
	 */
	public static final Database<Material> SURFACE_MATERIAL = new Database<>();
	/**
	 * A database of linear material (with length densities).
	 */
	public static final Database<Material> LINE_MATERIAL = new Database<>();
	
	
	
	static {
		
		// Add default materials
		// Note: Shear Modulus (G) values added based on standard engineering data (MatWeb, USDA Wood Handbook, lookpolymers).
		// Values are in Pascals.
		// Note: if the Shear modulus is not given, but the Young's modulus and Poisson's ratio are known, G can be calculated as:
		// G = E / (2*(1+v))

		// Plastics
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Acrylic", 1190, 1.7e9, MaterialGroup.PLASTICS));		// https://www.matweb.com/search/DataSheet.aspx?MatGUID=a5e93a1f1fff43bcbac5b6ca51b8981f&ckck=1
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Delrin", 1420, 0.946e9, MaterialGroup.PLASTICS));		// https://www.matweb.com/search/datasheet_print.aspx?matguid=0c568e14e05c4dba9dc2944331a042cd
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Nylon", 1150, 1.15e9, MaterialGroup.FIBERS)); // Nylon 6/6
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Polycarbonate (Lexan)", 1200, 0.786e9, MaterialGroup.PLASTICS));	// www.matweb.com/search/DataSheet.aspx?MatGUID=37807ef5e0134a0b80ca0a862bee2314 &
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Polystyrene", 1050, 1.23e9, MaterialGroup.PLASTICS));				// https://www.matweb.com/search/DataSheet.aspx?MatGUID=df6b1ef50ce84e7995bdd1f6fd1b04c9
		BULK_MATERIAL.add(newMaterial(Type.BULK, "PVC", 1390, 2.28e9, MaterialGroup.PLASTICS));			// https://www.matweb.com/search/DataSheet.aspx?MatGUID=bb6e739c553d4a34b199f0185e92f6f7 & https://www.lookpolymers.com/polymer_Overview-of-materials-for-PVC-Rigid-Grade.php?utm_source=chatgpt.com
		// Legacy component libraries use 946 kg/m3 for this blow-moulded material.
		// E and tensile yield use INEOS Eltex TUB433-NA00; compression uses
		// Densetec copolymer PP sheet.  G is derived from E with nu=0.45.
		// https://www.ineos.com/Show-Document/?BU=INEOS+O+%26+P+Europe&DocumentType=Technical+Data+Sheet&Grade=433-NA00
		// https://polymerindustries.com/wp-content/uploads/2018/03/Typical-Properties-PP-Copolymer.pdf
		// https://documents.dnrec.delaware.gov/Admin/Documents/dnrec-hearings/2020-P-W-0014/operations/2012-Operation-and-Maintenance-Manual-Part-1.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Polypropylene, bulk", 946, 0.517e9,
				1.50e9, 28e6, 30.3e6, 0.45, MaterialGroup.PLASTICS));

		// Tested Verbatim FDM PLA, print direction 1.  These values are a specific
		// 100%-infill print basis, not isotropic bulk-resin properties.
		// https://pmc.ncbi.nlm.nih.gov/articles/PMC7660314/
		// https://www.verbatim-europe.com/en/3d-printing-filaments/products/verbatim-pla-filament-175mm-1kg-black-55318
		BULK_MATERIAL.add(newMaterial(Type.BULK, "PLA - 100% infill", 1250, 1.24e9,
				2.71e9, 50e6, Double.NaN, 0.328, MaterialGroup.PLASTICS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "PLA (Verbatim FDM, 100% infill, direction 1)", 1240, 1.24e9,
				2.71e9, 50e6, Double.NaN, 0.328, MaterialGroup.PLASTICS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "PETG - 100% infill", 1250, 0.8e9, MaterialGroup.PLASTICS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "ABS - 100% infill", 1050, 0.875e9, MaterialGroup.PLASTICS));		// https://designerdata.nl/materials/plastics/thermo-plastics/acrylonitril-butadieen-styreen-general-purpose
		BULK_MATERIAL.add(newMaterial(Type.BULK, "ASA - 100% infill", 1050, 0.8e9, MaterialGroup.PLASTICS));		// https://designerdata.nl/materials/plastics/thermo-plastics/acrylonitrile---styrene---acrylester

		// Metals
		// Kaiser minimum yield for 6061-T6 sheet/plate is used instead of the
		// higher typical value.  G is derived from E and Poisson ratio.
		// https://online.kaiseraluminum.com/depot/PublicProductInformation/Document/1010/Kaiser_Aluminum_Sheet___Plate.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Aluminum", 2700, 25.68e9,
				68.3e9, 241e6, Double.NaN, 0.33, MaterialGroup.METALS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Aluminum 6061-T6", 2700, 25.68e9,
				68.3e9, 241e6, Double.NaN, 0.33, MaterialGroup.METALS));
		// Minimum for bare 7075-T6 sheet, 0.008-0.011 in (63 ksi yield).
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Aluminum 7075-T6", 2810, 26.95e9,
				71.7e9, 434e6, Double.NaN, 0.33, MaterialGroup.METALS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Brass", 8600, 38.9e9, MaterialGroup.METALS));		// https://www.matweb.com/search/DataSheet.aspx?MatGUID=d3bd4617903543ada92f4c101c2a20e5
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Steel", 7850, 79.7e9, MaterialGroup.METALS));		// https://www.matweb.com/search/DataSheet.aspx?MatGUID=210fcd12132049d0a3e0cabe7d091eef
		// Normalized at 870 C, air cooled, 13 mm round.
		// https://asm.matweb.com/search/SpecificMaterial.asp?bassnum=M4130B
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Steel 4130 normalized", 7850, 80e9,
				205e9, 460e6, Double.NaN, 0.29, MaterialGroup.METALS));
		// Outokumpu Core 304 hot-rolled/plate minimum proof strength; G is derived.
		// https://www.outokumpu.com/en/products/product-ranges/-/media/files/products/core/outokumpu-core-range-datasheet.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Stainless steel 304 annealed", 7900, 76.92e9,
				200e9, 210e6, Double.NaN, 0.30, MaterialGroup.METALS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Titanium", 4500, 43.0e9, MaterialGroup.METALS)); 	// https://www.matweb.com/search/DataSheet.aspx?MatGUID=66a15d609a3f4c829cb6ad08f0dafc01

		// Woods (Values are approximate G_LT / In-plane shear)
		// Longitudinal E and strength approximation at 170 kg/m3.  USDA reports
		// multiple direction-specific Poisson ratios, so no scalar value is stored.
		// https://www.fpl.fs.usda.gov/documnts/fplgtr/fplgtr282/fpl_gtr282.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Balsa", 170, 0.111e9,
				3.0e9, 14e6, 7e6, Double.NaN, MaterialGroup.WOODS));
		// American basswood at 12% moisture.  The structures tool uses modulus
		// of rupture as the conservative scalar plate/fin bending limit.
		// https://www.fpl.fs.usda.gov/documnts/fplgtr/fplgtr282/fpl_gtr282.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Basswood", 500, 0.331e9,
				10.1e9, 60e6, 32.6e6, Double.NaN, MaterialGroup.WOODS));
		// Yellow birch at approximately 12% moisture.  Tensile limit uses the
		// conservative modulus of rupture because this scalar model also serves
		// plate bending; G_LT/E_L = 0.068.
		// https://www.fpl.fs.usda.gov/documnts/fplgtr/fplgtr282/fpl_gtr282.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Birch", 670, 0.945e9,
				13.9e9, 114e6, 56.3e6, Double.NaN, MaterialGroup.WOODS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Cork", 240, 0.01e9, MaterialGroup.WOODS)); 	// https://www.makeitfrom.com/material-properties/Cork
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Maple", 755, 0.71e9, MaterialGroup.WOODS));	// GLT/EL = 0.074, EL = 9.6 GPa
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Pine", 530, 0.71e9, MaterialGroup.WOODS));	// GLT/EL = 0.081, EL = 8.8 GPa
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Plywood (birch)", 630, 0.613e9, MaterialGroup.WOODS));	// https://www.matweb.com/search/datasheet_print.aspx?matguid=bd6620450973496ea2578c283e9fb807 & Peak of Flight issue 615
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Spruce", 450, 1.1e9, MaterialGroup.WOODS));	// GLT/EL = 0.12, EL = 9.2 GPa

		// Composites
		// Hexcel typical high-strength carbon fabric laminate values (~60% Vf).
		// The generic name remains as a compatibility alias for existing designs.
		// https://www.hexcel.com/wp-content/uploads/2026/01/Prepreg_Technology-2.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Carbon fiber", 1600, 5.5e9,
				70e9, 800e6, 700e6, 0.05, MaterialGroup.COMPOSITES));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Carbon fiber/epoxy fabric (approx. 60% fiber volume)",
				1600, 5.5e9, 70e9, 800e6, 700e6, 0.05, MaterialGroup.COMPOSITES));
		// G10/FR4 in-plane elastic constants (NASA) with conservative crosswise
		// tensile and flatwise compression values from Atlas Fibre.  The generic
		// name remains as a compatibility alias for existing designs.
		// https://ntrs.nasa.gov/api/citations/19910006819/downloads/19910006819.pdf
		// https://www.atlasfibre.com/material/g-10/
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Fiberglass", 1800, 6.20e9,
				14.37e9, 262e6, 448e6, 0.159, MaterialGroup.COMPOSITES));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Fiberglass (G10/FR4 laminate, in-plane)", 1800, 6.20e9,
				14.37e9, 262e6, 448e6, 0.159, MaterialGroup.COMPOSITES));
		// Compatibility record for the legacy NASA Huntsville design.  Use the
		// room-temperature-dry measured means for a qualified 6781 S2-glass/
		// epoxy fabric laminate, not the much higher bare-fiber values listed in
		// the Structures workbook.  The saved 2000 kg/m3 density is retained.
		// https://www.wichita.edu/industry_and_defense/NIAR/Research/cytec-mtm45-1/Style-6781-S2-Glass-2.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "S2Fiberglass", 2000, 3.79e9,
				28.5e9, 551e6, 561e6, 0.138, MaterialGroup.COMPOSITES));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Kraft phenolic", 950, 1.78e9, MaterialGroup.COMPOSITES)); // Paper phenolic
		// Vulcanized-fibre supplier strength data, conservative cross direction
		// (CD), supplemented by measured Dynal vulcanized-fibre G12 and nu21.
		// https://alwaysreadyrocketry.com/wp-content/uploads/2020/01/Blue-Tube-Engineering-Data.pdf
		// https://www.dynamore.de/en/training/conferences/past/13th-european-ls-dyna-conference-2021/2021-eu-proceedings.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Blue tube", 1200, 2.529e9,
				5.516e9, 62.05e6, 241.3e6, 0.227, MaterialGroup.COMPOSITES));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Quantum tubing", 1050, 0, MaterialGroup.PLASTICS));

		// Paper/Foams (Low shear modulus, often negligible, but non-zero values provided where applicable)
		// Conservative short-duration paper-tube values.  The compression value
		// comes from a rocket-body-tube test at a matching 689 kg/m3 density.
		// https://pmc.ncbi.nlm.nih.gov/articles/PMC12430042/
		// https://engineering.ucdenver.edu/docs/librariesprovider29/college-of-engineering-and-applied-science/sp2020-capstone/mech4-report.pdf
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Cardboard", 680, 0.4e9,
				1.5e9, 8e6, 11.05e6, Double.NaN, MaterialGroup.PAPER));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Paper (office)", 820, 0.0, MaterialGroup.PAPER));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Depron (XPS)", 40, 0.0027e9, MaterialGroup.FOAMS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Styrofoam (generic EPS)", 20, 0.002e9, MaterialGroup.FOAMS));
		BULK_MATERIAL.add(newMaterial(Type.BULK, "Styrofoam \"Blue foam\" (XPS)", 32, 0.0028e9, MaterialGroup.FOAMS));


		// Surface Materials (Films/Fabrics - Shear Modulus generally not applicable for thin flexible membranes in this model)
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Ripstop nylon", 0.067, MaterialGroup.FABRICS));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Mylar", 0.021, MaterialGroup.PLASTICS));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Polyethylene (thin)", 0.015, MaterialGroup.PLASTICS));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Polyethylene (heavy)", 0.040, MaterialGroup.PLASTICS));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Silk", 0.060, MaterialGroup.FABRICS));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Paper (office)", 0.080, MaterialGroup.PAPER));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Cellophane", 0.018, MaterialGroup.PLASTICS));
		SURFACE_MATERIAL.add(newMaterial(Type.SURFACE, "Cr\u00eape paper", 0.025, MaterialGroup.PAPER));

		// Line Materials (1D tension members - Shear Modulus N/A)
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Thread (heavy-duty)", 0.0003, MaterialGroup.OTHER));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic cord (round 2 mm, 1/16 in)", 0.0018, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic cord (flat 6 mm, 1/4 in)", 0.0043, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic cord (flat 12 mm, 1/2 in)", 0.008, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic cord (flat 19 mm, 3/4 in)", 0.012, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic cord (flat 25 mm, 1 in)", 0.016, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Braided nylon (2 mm, 1/16 in)", 0.001, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Braided nylon (3 mm, 1/8 in)", 0.0035, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Tubular nylon (11 mm, 7/16 in)", 0.013, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Tubular nylon (14 mm, 9/16 in)", 0.016, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Tubular nylon (25 mm, 1 in)", 0.029, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar thread 138  (0.4 mm, 1/64 in)", 0.00014808, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar thread 207  (0.5 mm, 1/64 in)", 0.00023622, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar thread 346  (0.7 mm, 1/32 in)", 0.00047243, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar thread 415  (0.8 mm, 1/32 in)", 0.00055117, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar thread 800 (1.1 mm, 3/64 in)", 0.00099211, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (3.2 mm, 1/8 in)", 0.00967306, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (4.8 mm, 3/16 in)", 0.01785797, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (6.4 mm, 1/4 in)", 0.02976328, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (7.9 mm, 5/16 in)", 0.04464491, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (10 mm, 3/8 in)", 0.05952655, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (11 mm, 7/16 in)", 0.07440819, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (13 mm, 1/2 in)", 0.11607678, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (14 mm, 9/16 in)", 0.20834293, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (16 mm, 5/8 in)", 0.28721562, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (19 mm, 3/4 in)", 0.3497185, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Kevlar 12-strand (25 mm, 1 in)", 0.45686629, MaterialGroup.KEVLARS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Nylon flat webbing md. (10 mm, 3/8 in)", 0.00951444, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Nylon flat webbing md. (13 mm, 1/2  in)", 0.01334208, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Nylon flat webbing md. (16 mm, 5/8 in)", 0.01618548, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Nylon flat webbing lg. (14 mm, 9/16 in)", 0.02723097, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Nylon flat webbing lg. (25 mm, 1 in)", 0.03969816, MaterialGroup.NYLONS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Paraline small IIIA (6.4 mm, 1.4 in)", 0.00371829, MaterialGroup.OTHER));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic rubber band (flat 3.2 mm, 1/8 in)", 0.00297638, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic rubber band (flat 6.4 mm, 1/4 in)", 0.00613107, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (flat 3.2 mm, 1/8 in)", 0.00106, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (flat 4 mm, 5/32 in)", 0.002, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (flat 6.4 mm, 1/4 in)", 0.00254, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (round 2 mm, 1/16 in)", 0.0035, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (round 2.5 mm, 3/32 in)", 0.0038, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (flat 10 mm, 3/8 in)", 0.00381, MaterialGroup.ELASTICS));
		LINE_MATERIAL.add(newMaterial(Type.LINE, "Elastic braided cord (flat 13 mm, 1/2 in)", 0.00551172, MaterialGroup.ELASTICS));


		// Add user-defined materials
		for (Material m : Application.getPreferences().getUserMaterials()) {
			switch (m.getType()) {
				case LINE:
					LINE_MATERIAL.add(m);
					break;

				case SURFACE:
					SURFACE_MATERIAL.add(m);
					break;

				case BULK:
					BULK_MATERIAL.add(m);
					break;

				default:
					log.warn("ERROR: Unknown material type " + m);
			}
		}

		// Add database storage listener
		MaterialStorage listener = new MaterialStorage();
		LINE_MATERIAL.addDatabaseListener(listener);
		SURFACE_MATERIAL.addDatabaseListener(listener);
		BULK_MATERIAL.addDatabaseListener(listener);
	}

	/**
	 * builds a new material based on the parameters given
	 * @param type		The type of material
	 * @param baseName	the name of material
	 * @param density	density (in kg/m3)
	 * @param inPlaneShearModulus	the in-plane shear modulus G (in Pa)
	 * @param group		the material group
	 * @return	a new object with the material data
	 */
	private static Material newMaterial(Type type, String baseName, double density, double inPlaneShearModulus, MaterialGroup group) {
		String name = trans.get("material", baseName);
		return Material.newMaterial(type, name, density, inPlaneShearModulus, group, false);
	}

	/**
	 * Builds a default material with the structural properties used by the structures tool.
	 * Unknown properties must be represented by {@link Double#NaN}; values are never
	 * inferred from the material name at runtime.
	 */
	private static Material newMaterial(Type type, String baseName, double density, double inPlaneShearModulus,
			double youngsModulus, double tensileStrength, double compressiveStrength, double poissonRatio,
			MaterialGroup group) {
		String name = trans.get("material", baseName);
		return Material.newMaterial(type, name, density, inPlaneShearModulus, youngsModulus, tensileStrength,
				compressiveStrength, poissonRatio, group, false, false);
	}

	/**
	 * builds a new material based on the parameters given
	 * @param type		The type of material
	 * @param baseName	the name of material
	 * @param density	density
	 * @param group		the material group
	 * @return	a new object with the material data
	 */
	private static Material newMaterial(Type type, String baseName, double density, MaterialGroup group) {
		return newMaterial(type, baseName, density, 0.0, group);
	}

	private static Material newMaterial(Type type, String baseName, double density) {
		return newMaterial(type, baseName, density, null);
	}




	/*
	 * Used just for ensuring initialization of the class.
	 */
	public static void fakeMethod() {

	}

	/**
	 * Find a material from the database or return a new user defined material if the specified
	 * material with the specified density and in-plane shear modulus is not found.
	 * <p>
	 * This method will attempt to localize the material name to the current locale, or use
	 * the provided name if unable to do so.
	 *
	 * @param type				the material type.
	 * @param baseName			the base name of the material.
	 * @param density			the density of the material.
	 * @param inPlaneShearModulus	the in-plane shear modulus G (in Pa).
	 * @param group				the material group.
	 * @return					the material object from the database or a new material.
	 */
	public static Material findMaterial(Material.Type type, String baseName, double density, double inPlaneShearModulus, MaterialGroup group) {
		Database<Material> db = getDatabase(type);
		String name = trans.get("material", baseName);

		for (Material m : db) {
			// Material group comparison is omitted to keep compatibility with files pre OR 24.12
			if (m.getName().equalsIgnoreCase(name) && MathUtil.equals(m.getDensity(), density) 
					&& MathUtil.equals(m.getInPlaneShearModulus(), inPlaneShearModulus)) {
				return m;
			}
		}
		return Material.newMaterial(type, name, density, inPlaneShearModulus, group, true, true);
	}

	/**
	 * Loads a pre-structural-properties material record.  When its stable database
	 * name and density identify a known material, use the current database
	 * mechanical properties while retaining the file's density for mass
	 * compatibility.  The density bound prevents an unrelated custom material
	 * with a reused name from receiving an unsafe override.
	 */
	public static Material findLegacyMaterial(Material.Type type, String baseName, double density,
			Double inPlaneShearModulus, MaterialGroup group) {
		Material loaded = inPlaneShearModulus == null
				? findMaterial(type, baseName, density, group)
				: findMaterial(type, baseName, density, inPlaneShearModulus, group);
		if (type != Material.Type.BULK || hasStructuralProperties(loaded)) {
			return loaded;
		}

		Material reference = findMaterial(type, baseName);
		if (reference == null || !hasStructuralProperties(reference)
				|| !legacyDensityMatches(density, reference.getDensity())) {
			return loaded;
		}
		return Material.newMaterial(type, loaded.getName(), density, reference.getInPlaneShearModulus(),
				reference.getYoungsModulus(), reference.getTensileStrength(), reference.getCompressiveStrength(),
				reference.getPoissonRatio(), reference.getGroup(), true, true);
	}

	private static boolean hasStructuralProperties(Material material) {
		return material != null && optionalPositive(material.getYoungsModulus()) > 0
				&& optionalPositive(material.getTensileStrength()) > 0;
	}

	private static boolean legacyDensityMatches(double loadedDensity, double referenceDensity) {
		if (!Double.isFinite(loadedDensity) || !Double.isFinite(referenceDensity)
				|| loadedDensity <= 0 || referenceDensity <= 0) {
			return false;
		}
		return Math.abs(loadedDensity - referenceDensity) / referenceDensity <= 0.15;
	}

	/**
	 * Find a material with explicit structural properties, or create a document
	 * material when no exact match exists.  These values are part of the
	 * material identity so a component cannot accidentally inherit properties
	 * from another material with the same name and density.
	 */
	public static Material findMaterial(Material.Type type, String baseName, double density, double inPlaneShearModulus,
			double youngsModulus, double tensileStrength, double compressiveStrength, double poissonRatio,
			MaterialGroup group) {
		Database<Material> db = getDatabase(type);
		String name = trans.get("material", baseName);
		youngsModulus = optionalPositive(youngsModulus);
		tensileStrength = optionalPositive(tensileStrength);
		compressiveStrength = optionalPositive(compressiveStrength);
		poissonRatio = optionalPoissonRatio(poissonRatio);

		for (Material material : db) {
			if (material.getName().equalsIgnoreCase(name) && MathUtil.equals(material.getDensity(), density)
					&& MathUtil.equals(material.getInPlaneShearModulus(), inPlaneShearModulus)
					&& sameProperty(material.getYoungsModulus(), youngsModulus)
					&& sameProperty(material.getTensileStrength(), tensileStrength)
					&& sameProperty(material.getCompressiveStrength(), compressiveStrength)
					&& sameProperty(material.getPoissonRatio(), poissonRatio)) {
				return material;
			}
		}
		return Material.newMaterial(type, name, density, inPlaneShearModulus, youngsModulus, tensileStrength,
				compressiveStrength, poissonRatio, group, true, true);
	}

	private static boolean sameProperty(double first, double second) {
		return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
	}

	private static double optionalPositive(double value) {
		return Double.isFinite(value) && value > 0 ? value : Double.NaN;
	}

	private static double optionalPoissonRatio(double value) {
		return Double.isFinite(value) && value > 0 && value < 0.5 ? value : Double.NaN;
	}

	/**
	 * Find a material from the database or return a new user defined material if the specified
	 * material with the specified density is not found.
	 * <p>
	 * This method will attempt to localize the material name to the current locale, or use
	 * the provided name if unable to do so.
	 *
	 * @param type			the material type.
	 * @param baseName		the base name of the material.
	 * @param density		the density of the material.
	 * @param group			the material group.
	 * @return				the material object from the database or a new material.
	 */
	public static Material findMaterial(Material.Type type, String baseName, double density, MaterialGroup group) {
		Database<Material> db = getDatabase(type);
		String name = trans.get("material", baseName);

		for (Material m : db) {
			// Backward-compatible lookup: match by name and density only.
			if (m.getName().equalsIgnoreCase(name) && MathUtil.equals(m.getDensity(), density)) {
				return m;
			}
		}
		return Material.newMaterial(type, name, density, 0.0, group, true, true);
	}

	/**
	 * Find a material from the database or return a new user defined material if the specified
	 * material with the specified density is not found.
	 * <p>
	 * This method will attempt to localize the material name to the current locale, or use
	 * the provided name if unable to do so.
	 *
	 * @param type			the material type.
	 * @param baseName		the base name of the material.
	 * @param density		the density of the material.
	 * @return				the material object from the database or a new material.
	 */
	public static Material findMaterial(Material.Type type, String baseName, double density) {
		return findMaterial(type, baseName, density, null);
	}

	/**
	 * Find a material from the database with the specified type and name.  Returns
	 * <code>null</code> if the specified material could not be found.
	 * <p>
	 * This method will attempt to localize the material name to the current locale, or use
	 * the provided name if unable to do so.
	 *
	 * @param type		the material type.
	 * @param baseName	the material base name in the database.
	 * @return			the material, or <code>null</code> if not found.
	 */
	public static Material findMaterial(Material.Type type, String baseName) {
		Database<Material> db = getDatabase(type);
		String name = trans.get("material", baseName);
		
		for (Material m : db) {
			if (m.getName().equalsIgnoreCase(name)) {
				return m;
			}
		}
		return null;
	}

	/**
	 * gets the specific database with the given type
	 * @param 	type	the desired type
	 * @return	the database of the type given
	 */
	public static Database<Material> getDatabase(Material.Type type) {
		return switch (type) {
			case BULK -> BULK_MATERIAL;
			case SURFACE -> SURFACE_MATERIAL;
			case LINE -> LINE_MATERIAL;
			default -> throw new IllegalArgumentException("Illegal material type: " + type);
		};
	}

	public static void addMaterial(Material material) {
		Database<Material> db = getDatabase(material.getType());
		db.add(material);
	}

	public static void removeMaterial(Material material) {
		Database<Material> db = getDatabase(material.getType());
		db.remove(material);
	}
	
}
