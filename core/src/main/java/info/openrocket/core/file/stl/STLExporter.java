package info.openrocket.core.file.stl;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.ObjFace;
import info.openrocket.core.file.wavefrontobj.DefaultObj;
import info.openrocket.core.file.wavefrontobj.ObjUtils;
import info.openrocket.core.file.wavefrontobj.export.OBJExportOptions;
import info.openrocket.core.file.wavefrontobj.export.OBJExporterFactory;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Exports rocket components to a binary STL (Stereolithography) file for 3D printing.
 * <p>
 * This exporter reuses the OBJ export geometry pipeline to generate a triangulated mesh,
 * then converts the result to binary STL format. Binary STL is preferred over ASCII STL
 * because it produces smaller files and is faster to write.
 * <p>
 * Binary STL format:
 * <ul>
 *   <li>80-byte header (arbitrary text)</li>
 *   <li>4-byte uint32 number of triangles</li>
 *   <li>For each triangle:
 *     <ul>
 *       <li>12 bytes: normal vector (3 x float32)</li>
 *       <li>36 bytes: 3 vertices (3 x 3 x float32)</li>
 *       <li>2 bytes: attribute byte count (unused, set to 0)</li>
 *     </ul>
 *   </li>
 * </ul>
 * All multi-byte values are little-endian.
 *
 * @author Alex Zoghlin
 */
public class STLExporter {

    private static final Logger log = LoggerFactory.getLogger(STLExporter.class);

    private static final int HEADER_SIZE = 80;
    private static final int TRIANGLE_RECORD_SIZE = 50; // 12 + 36 + 2 bytes

    private final List<RocketComponent> components;
    private final FlightConfiguration configuration;
    private final STLExportOptions stlOptions;
    private final File file;
    private final WarningSet warnings;

    /**
     * Creates an STL exporter.
     *
     * @param components    List of components to export
     * @param configuration Flight configuration to use for the export
     * @param file          The file to export the STL to
     * @param stlOptions    STL export options
     * @param warnings      Warning set for reporting issues
     */
    public STLExporter(List<RocketComponent> components, FlightConfiguration configuration,
                       File file, STLExportOptions stlOptions, WarningSet warnings) {
        this.components = components;
        this.configuration = configuration;
        this.file = file;
        this.stlOptions = stlOptions;
        this.warnings = warnings;
    }

    /**
     * Performs the STL export by first generating geometry via the OBJ pipeline,
     * then writing the result in binary STL format.
     */
    public void doExport() {
        // Generate triangulated mesh using the OBJ pipeline
        DefaultObj obj = generateMesh();

        // Write binary STL
        writeBinarySTL(obj, file);
    }

    /**
     * Generates the triangulated mesh by leveraging the OBJ export pipeline components.
     * We create a DefaultObj, populate it with geometry from each component,
     * then triangulate and apply transforms.
     *
     * @return The triangulated DefaultObj containing all mesh data
     */
    private DefaultObj generateMesh() {
        // Build OBJ options optimized for STL export (always triangulate, no appearance)
        OBJExportOptions objOptions = buildOBJOptions();

        // Use a temporary file for the OBJ exporter (it needs a file reference)
        File tempFile;
        try {
            tempFile = File.createTempFile("openrocket_stl_", ".obj");
            tempFile.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary file for STL export", e);
        }

        try {
            // Run the OBJ export pipeline - this writes the OBJ file
            OBJExporterFactory objExporter = new OBJExporterFactory(
                    components, configuration, tempFile, objOptions, warnings);
            objExporter.doExport();

            // Read it back as a DefaultObj
            return readObjFromFile(tempFile);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Read an OBJ file back into a DefaultObj.
     */
    private DefaultObj readObjFromFile(File objFile) {
        try (java.io.InputStream is = new java.io.FileInputStream(objFile)) {
            de.javagl.obj.Obj rawObj = de.javagl.obj.ObjReader.read(is);

            // Convert to our DefaultObj
            DefaultObj obj = new DefaultObj();
            for (int i = 0; i < rawObj.getNumVertices(); i++) {
                FloatTuple v = rawObj.getVertex(i);
                obj.addVertex(v);
            }
            for (int i = 0; i < rawObj.getNumNormals(); i++) {
                FloatTuple n = rawObj.getNormal(i);
                obj.addNormal(n);
            }
            for (int i = 0; i < rawObj.getNumFaces(); i++) {
                ObjFace face = rawObj.getFace(i);
                int numVerts = face.getNumVertices();
                int[] vertexIndices = new int[numVerts];
                int[] normalIndices = null;

                boolean hasNormals = face.containsNormalIndices();
                if (hasNormals) {
                    normalIndices = new int[numVerts];
                }

                for (int j = 0; j < numVerts; j++) {
                    vertexIndices[j] = face.getVertexIndex(j);
                    if (hasNormals) {
                        normalIndices[j] = face.getNormalIndex(j);
                    }
                }
                obj.addFace(vertexIndices, null, normalIndices);
            }
            return obj;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read intermediate OBJ file for STL conversion", e);
        }
    }

    /**
     * Build OBJ export options from STL options. Always forces triangulation and disables appearance.
     */
    private OBJExportOptions buildOBJOptions() {
        OBJExportOptions objOptions = new OBJExportOptions(configuration.getRocket());
        objOptions.setExportChildren(stlOptions.isExportChildren());
        objOptions.setExportAllInstances(stlOptions.isExportAllInstances());
        objOptions.setExportMotors(stlOptions.isExportMotors());
        objOptions.setRemoveOffset(stlOptions.isRemoveOffset());
        objOptions.setLOD(stlOptions.getLOD());
        objOptions.setTransformer(stlOptions.getTransformer());
        objOptions.setScaling(stlOptions.getScaling());

        // STL-specific: always triangulate, never export appearance or separate files
        objOptions.setTriangulate(true);
        objOptions.setTriangulationMethod(ObjUtils.TriangulationMethod.DELAUNAY);
        objOptions.setExportAppearance(false);
        objOptions.setExportAsSeparateFiles(false);

        return objOptions;
    }

    /**
     * Write the mesh data from a DefaultObj as a binary STL file.
     *
     * @param obj  The triangulated mesh
     * @param file The output STL file
     */
    private void writeBinarySTL(DefaultObj obj, File file) {
        int numFaces = obj.getNumFaces();

        // Count total triangles (each face should already be a triangle after triangulation,
        // but handle non-triangles by fan-triangulating them)
        int totalTriangles = 0;
        for (int i = 0; i < numFaces; i++) {
            ObjFace face = obj.getFace(i);
            int numVerts = face.getNumVertices();
            if (numVerts >= 3) {
                totalTriangles += (numVerts - 2); // fan triangulation
            }
        }

        try (OutputStream out = new FileOutputStream(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + 4 + totalTriangles * TRIANGLE_RECORD_SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Write 80-byte header
            byte[] header = new byte[HEADER_SIZE];
            String headerText = "STL exported by OpenRocket";
            byte[] headerBytes = headerText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(headerBytes, 0, header, 0, Math.min(headerBytes.length, HEADER_SIZE));
            buffer.put(header);

            // Write number of triangles
            buffer.putInt(totalTriangles);

            // Write each triangle
            for (int i = 0; i < numFaces; i++) {
                ObjFace face = obj.getFace(i);
                int numVerts = face.getNumVertices();

                if (numVerts < 3) {
                    continue;
                }

                // Fan triangulate: vertex 0 is the pivot
                FloatTuple v0 = obj.getVertex(face.getVertexIndex(0));

                for (int j = 1; j < numVerts - 1; j++) {
                    FloatTuple v1 = obj.getVertex(face.getVertexIndex(j));
                    FloatTuple v2 = obj.getVertex(face.getVertexIndex(j + 1));

                    // Compute face normal from cross product
                    float ux = v1.getX() - v0.getX();
                    float uy = v1.getY() - v0.getY();
                    float uz = v1.getZ() - v0.getZ();
                    float vx = v2.getX() - v0.getX();
                    float vy = v2.getY() - v0.getY();
                    float vz = v2.getZ() - v0.getZ();

                    float nx = uy * vz - uz * vy;
                    float ny = uz * vx - ux * vz;
                    float nz = ux * vy - uy * vx;

                    // Normalize
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len > 0) {
                        nx /= len;
                        ny /= len;
                        nz /= len;
                    }

                    // Normal
                    buffer.putFloat(nx);
                    buffer.putFloat(ny);
                    buffer.putFloat(nz);

                    // Vertex 1
                    buffer.putFloat(v0.getX());
                    buffer.putFloat(v0.getY());
                    buffer.putFloat(v0.getZ());

                    // Vertex 2
                    buffer.putFloat(v1.getX());
                    buffer.putFloat(v1.getY());
                    buffer.putFloat(v1.getZ());

                    // Vertex 3
                    buffer.putFloat(v2.getX());
                    buffer.putFloat(v2.getY());
                    buffer.putFloat(v2.getZ());

                    // Attribute byte count (unused)
                    buffer.putShort((short) 0);
                }
            }

            out.write(buffer.array());
            log.info("STL file written successfully: {} ({} triangles)", file.getAbsolutePath(), totalTriangles);

        } catch (IOException e) {
            throw new RuntimeException("Failed to write STL file: " + file.getAbsolutePath(), e);
        }
    }
}
