package it.maivisto.models;
import java.io.Serializable;
import java.util.Map;
import org.grouplens.grapht.annotation.DefaultProvider;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Shareable;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * Co-occurrence matrix.
 */
@DefaultProvider(CoOccurrenceMatrixModelBuilder.class)
@Shareable
public class CoOccurrenceMatrixModel implements Serializable, ItemItemModel {
	private static final long serialVersionUID = 1L;

	private final Map<Long,ImmutableSparseVector> model;

	public CoOccurrenceMatrixModel(Map<Long,ImmutableSparseVector> nbrs) {
		model = nbrs;
	}

	/**
     * Get the set of all items in the model.
     * @return The set of item IDs for all items in the model.
     */
	@Override
	public LongSortedSet getItemUniverse() {	
		return LongUtils.packedSet(model.keySet());
	}

	/**
     * Get the neighbors of an item scored by similarity. This is the corresponding row of the co-occurrence matrix.
     * @param item The item to get the neighborhood for.
     * @return The row of the co-occurrence matrix. If the item is unknown, an empty vector is returned.
     */
	@Override
	public SparseVector getNeighbors(long item) {
		if (model.containsKey(item))
			return model.get(item);
		else
			return ImmutableSparseVector.empty();
	}
}
