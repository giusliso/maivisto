package it.maivisto.models;
import java.util.HashMap;
import java.util.Map;
import org.grouplens.grapht.annotation.DefaultProvider;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Shareable;
import org.grouplens.lenskit.knn.item.model.ItemItemModelBuilder;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import it.maivisto.utility.Utilities;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * Cosine similarity matrix. 
 * Extends the default SimilarityMatrixModel normalizing the similarity values.
 */
@DefaultProvider(ItemItemModelBuilder.class)
@Shareable
public class CosineSimilarityMatrixModel extends SimilarityMatrixModel{
	private static final long serialVersionUID = 1L;

	private final Map<Long,ImmutableSparseVector> model;
	
	public CosineSimilarityMatrixModel(Map<Long,ImmutableSparseVector> nbrs) {
		super(nbrs);
		model = normalize(nbrs);
	}

	/**
     * It gets the set of all items in the model.
     * @return The set of item IDs for all items in the model.
     */
	@Override
	public LongSortedSet getItemUniverse() {	
		return LongUtils.packedSet(model.keySet());
	}

	/**
     * It gets the neighbors of an item scored by similarity. This is the corresponding row of the cosine similarity matrix.
     * @param item The item to get the neighborhood for.
     * @return The row of the normalized cosine similarity matrix. If the item is unknown, an empty vector is returned.
     */
	@Override
	public SparseVector getNeighbors(long item) {
		if (model.containsKey(item))
			return model.get(item);
		else
			return ImmutableSparseVector.empty();
	}
	
	/**
	 * It normalizes the cosine similarity matrix.
	 * @param model The matrix to normalize.
	 * @return The normalized matrix.
	 */
	private Map<Long, ImmutableSparseVector> normalize(Map<Long, ImmutableSparseVector> model) {
		Map<Long, ImmutableSparseVector> mod = new HashMap<Long, ImmutableSparseVector>();
		for(Long i : model.keySet()){
			MutableSparseVector v = MutableSparseVector.create(model.get(i).keyDomain());
			
			for(VectorEntry vec : model.get(i))			
				v.set(vec.getKey(), Utilities.normalize(-1, 1, vec.getValue(), 0, 1));
			
			model.put(i, v.immutable());
		}
		return mod;
	}
}
