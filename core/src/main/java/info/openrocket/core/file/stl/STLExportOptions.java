package info.openrocket.core.file.stl;

import info.openrocket.core.file.wavefrontobj.CoordTransform;
import info.openrocket.core.file.wavefrontobj.DefaultCoordTransform;
import info.openrocket.core.file.wavefrontobj.ObjUtils;
import info.openrocket.core.rocketcomponent.Rocket;

/**
 * Options for exporting rocket components to a binary STL file.
 *
 * @author Alex Zoghlin
 */
public class STLExportOptions {

    /**
     * If true, export all children of the components as well.
     */
    private boolean exportChildren;

    /**
     * If false, export only a single instance of each component.
     */
    private boolean exportAllInstances;

    /**
     * If true, export the motors of the components as well.
     */
    private boolean exportMotors;

    /**
     * If true, remove the offset of the object so it is centered at the origin.
     */
    private boolean removeOffset;

    /**
     * The level of detail to use for the export.
     */
    private ObjUtils.LevelOfDetail LOD;

    /**
     * The coordinate transformer to use for the export.
     */
    private CoordTransform transformer;

    /**
     * The scaling factor to use for the export (1 = no scaling).
     */
    private float scaling;

    public STLExportOptions(Rocket rocket) {
        this.exportChildren = false;
        this.exportAllInstances = true;
        this.exportMotors = false;
        this.removeOffset = true;
        this.LOD = ObjUtils.LevelOfDetail.HIGH_QUALITY;
        this.transformer = new DefaultCoordTransform(rocket.getLength());
        this.scaling = 1000.0f; // Default to mm for 3D printing
    }

    public boolean isExportChildren() {
        return exportChildren;
    }

    public void setExportChildren(boolean exportChildren) {
        this.exportChildren = exportChildren;
    }

    public boolean isExportAllInstances() {
        return exportAllInstances;
    }

    public void setExportAllInstances(boolean exportAllInstances) {
        this.exportAllInstances = exportAllInstances;
    }

    public boolean isExportMotors() {
        return exportMotors;
    }

    public void setExportMotors(boolean exportMotors) {
        this.exportMotors = exportMotors;
    }

    public boolean isRemoveOffset() {
        return removeOffset;
    }

    public void setRemoveOffset(boolean removeOffset) {
        this.removeOffset = removeOffset;
    }

    public ObjUtils.LevelOfDetail getLOD() {
        return LOD;
    }

    public void setLOD(ObjUtils.LevelOfDetail LOD) {
        this.LOD = LOD;
    }

    public CoordTransform getTransformer() {
        return transformer;
    }

    public void setTransformer(CoordTransform transformer) {
        this.transformer = transformer;
    }

    public float getScaling() {
        return scaling;
    }

    public double getScalingDouble() {
        return scaling;
    }

    public void setScaling(float scaling) {
        this.scaling = scaling;
    }

    public void setScalingDouble(double scaling) {
        this.scaling = (float) scaling;
    }
}
