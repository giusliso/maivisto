package it.maivisto.recommender;

import java.util.HashMap;
import java.util.Set;

import org.grouplens.lenskit.knn.item.model.ItemItemModel;

/**
 * Class to manage different models.
 */
public class ModelsManager {

	private HashMap<Integer, ItemItemModel> mapModel;
	private HashMap<Integer, Double> mapWeight;

	public ModelsManager() {
		mapModel = new HashMap<Integer, ItemItemModel>();
		mapWeight = new HashMap<Integer, Double>();
	}

	/**
	 * It adds a model and its weight to the manager.
	 * @param model The item-item model.
	 * @param weight The weight associated.
	 */
	public void addModel(ItemItemModel model, double weight){
		int id = getFreeId();
		mapModel.put(id, model);
		mapWeight.put(id, weight);
	}

	/**
	 * It gets the weight of a model.
	 * @param modelId Model identifier.
	 * @return The weight.
	 */
	public double getModelWeight(int modelId){
		return mapWeight.get(modelId);
	}

	/**
	 * It gets the model identified by its identifier. 
	 * @param modelId Model identifier.
	 * @return The model.
	 */
	public ItemItemModel getModel(int modelId){
		return mapModel.get(modelId);
	}

	/**
	 * It gets the set of all models IDs.
	 * @return The IDs set.
	 */
	public Set<Integer> modelsIdSet(){
		return mapModel.keySet();
	}

	/**
	 * Gets the first free ID to associated to a model.
	 * @return A free ID.
	 */
	private int getFreeId(){
		int count=0;
		while(mapModel.containsKey(count))
			count++;
		return count;
	}
}