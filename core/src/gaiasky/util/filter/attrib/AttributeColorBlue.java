package gaiasky.util.filter.attrib;

import gaiasky.scene.api.IParticleRecord;
import gaiasky.util.i18n.I18n;

public class AttributeColorBlue extends AttributeAbstract implements IAttribute {

    @Override
    public double get(IParticleRecord bean) {
        return bean.rgb()[2];
    }

    @Override
    public String getUnit() {
        return I18n.msg("gui.attrib.color.unit");
    }

    public String toString(){
        return I18n.msg("gui.attrib.color.blue");
    }
}
