package gaiasky.scene.view;

import gaiasky.scene.Mapper;
import gaiasky.scene.component.DatasetDescription;
import gaiasky.scene.component.ParticleSet;

public class FilterView extends BaseView {

    private ParticleSet set;
    private DatasetDescription dataset;

    public FilterView() {
    }

    @Override
    protected void entityChanged() {
        super.entityChanged();
        this.dataset = Mapper.datasetDescription.get(entity);
        this.set = Mapper.particleSet.has(entity) ? Mapper.particleSet.get(entity) : Mapper.starSet.get(entity);
    }

    public boolean filter(int i) {
        if (set == null) {
            return false;
        }
        if (dataset != null && dataset.catalogInfo != null && dataset.catalogInfo.filter != null) {
            return dataset.catalogInfo.filter.evaluate(set.pointData.get(i));
        }
        return true;
    }

}
