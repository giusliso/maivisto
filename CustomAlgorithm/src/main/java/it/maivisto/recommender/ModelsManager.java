package it.maivisto.recommender;

import java.util.HashMap;
import java.util.Set;

import org.grouplens.lenskit.knn.item.model.ItemItemModel;

/**
 * Manage different models
 */
public class ModelsManager {

	private HashMap<Integer, ItemItemModel> mapModel;
	private HashMap<Integer, Double> mapWeight;
	
	public ModelsManager() {
		mapModel = new HashMap<Integer, ItemItemModel>();
		mapWeight = new HashMap<Integer, Double>();
	}
	

	public void addModel(ItemItemModel model, double weight){
		int id = getFreeId();
		mapModel.put(id, model);
		mapWeight.put(id, weight);
	}

	public double getModelWeight(int modelId){
		return mapWeight.get(modelId);
	}
	
	public ItemItemModel getModel(int modelId){
		return mapModel.get(modelId);
	}
	
	public Set<Integer> modelsIdSet(){
		return mapModel.keySet();
	}
	
	private int getFreeId(){
		int count=0;
		while(mapModel.containsKey(count))
			count++;
		return count;
	}
}