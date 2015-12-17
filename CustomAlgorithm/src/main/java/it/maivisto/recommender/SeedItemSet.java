package it.maivisto.recommender;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.cursors.Cursors;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemEventDAO;
import org.grouplens.lenskit.data.dao.SortOrder;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.source.DataSource;

/**
 * Class to retrieve the standard seed items set. 
 */
public class SeedItemSet {

	public static enum Period {LAST_WEEK, LAST_MONTH, LAST_YEAR, EVER};

	private HashSet<Long> set;
	private ItemEventDAO iedao;
	private ItemDAO idao;
	private EventDAO dao;

	private Date firstTimestamp = null;
	private Date lastTimestamp = null;

	public SeedItemSet(DataSource dataset) {
		this.iedao = dataset.getItemEventDAO();
		this.idao = dataset.getItemDAO();
		this.dao = dataset.getEventDAO();
		this.set = new HashSet<Long>();
	}

	public SeedItemSet(EventDAO dao) {
		this.iedao = new PrefetchingItemEventDAO(dao);
		this.idao = new PrefetchingItemDAO(dao);
		this.dao = dao;
		this.set = new HashSet<Long>();
	}


	/**
	 * Adds an item to the standard seed items set.
	 * @param item The item to add.
	 */
	private void addItem(Long item){
		if(item != null)
			set.add(item);
	}

	/**
	 * Retrieves the four standard seed items.
	 * - the most popular item of ever;
	 * - the most popular item of the last week;
	 * - the last positevely rated item;
	 * - the last added item not rated.
	 * @return The standard seed items set.
	 */
	public Set<Long> getStandardSeedItemSet() {
		if(set.isEmpty()){
			addItem(getMostPopularItem(Period.EVER));
			addItem(getMostPopularItem(Period.LAST_WEEK));
			addItem(getLastPositivelyRatedItem());
			addItem(getLastItemAddedNotRated());
		}
		return set;
	}

	/**
	 * Gets the most popular item, i.e. the item with the greatest number of ratings, in a certain period of time (last week/month/year/ever).
	 * @param period Period of time to consider.
	 * @return The most popular item.
	 */
	@SuppressWarnings("deprecation")
	public Long getMostPopularItem(Period period) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(getLastTimestamp()); 

		Date thresholdDate = null;
		switch (period) {
		case LAST_WEEK: cal.add(Calendar.DATE, -7); thresholdDate=cal.getTime(); break;
		case LAST_MONTH:cal.add(Calendar.DATE, -30); thresholdDate=cal.getTime();  break;
		case LAST_YEAR: cal.add(Calendar.DATE, -365); thresholdDate=cal.getTime(); break;
		case EVER: break;
		}

		Long idItemMostPopular = null;
		int max=0;
		for(Long itemId : idao.getItemIds()){
			List<Event> events = iedao.getEventsForItem(itemId);
			int count = 0;
			if(thresholdDate == null)
				count = events.size();
			else
			{
				thresholdDate.setHours(0);
				thresholdDate.setMinutes(0);
				thresholdDate.setSeconds(0);
				for(Event ev : events){
					Date rateDate = new Date(ev.getTimestamp()*1000);
					if(rateDate.after(thresholdDate))
						count++;
				}
			}

			if(count > max){
				idItemMostPopular=itemId;
				max=count;
			}
		}

		return idItemMostPopular;
	}

	/**
	 * Gets the timestamp of the first rating in the dataset.
	 * @return The timestamp.
	 */
	private Date getFirstTimestamp(){

		if(firstTimestamp == null){
			Cursor<Rating> events = dao.streamEvents(Rating.class, SortOrder.TIMESTAMP);
			ArrayList<Rating> ratings = Cursors.makeList(events);	
			if(ratings.size() == 0)
				firstTimestamp = Calendar.getInstance().getTime();
			else
				firstTimestamp = new Date(ratings.get(0).getTimestamp()*1000);
		}
		return firstTimestamp;
	}

	/**
	 * Gets the timestamp of the last rating in the dataset.
	 * @return The timestamp.
	 */
	private Date getLastTimestamp(){

		if(lastTimestamp == null){
			Cursor<Rating> events = dao.streamEvents(Rating.class, SortOrder.TIMESTAMP);
			ArrayList<Rating> ratings = Cursors.makeList(events);						
			if(ratings.size() == 0)
				lastTimestamp = Calendar.getInstance().getTime();
			else
				lastTimestamp = new Date(ratings.get(ratings.size()-1).getTimestamp()*1000);
		}
		return lastTimestamp;
	}



	/**
	 * Gets the last positively rated item (the last item added in the platform rated in a positive way).
	 * An item is "positively rated" if its rate is equals or greater than the global mean of all ratings.
	 * @return The last positively rated item.
	 */
	public Long getLastPositivelyRatedItem(){
		Long lastPositivelyRatedItem = null;
		Date recentDate = getFirstTimestamp(); 

		for(Long itemId : idao.getItemIds()){
			List<Rating> ratings = iedao.getEventsForItem(itemId, Rating.class);
			int threshold = getPositiveRatingThreshold(ratings);
			Date date = getFirstTimestamp();

			for(Rating rating : ratings){
				Date dateR = new Date(rating.getTimestamp()*1000);
				if(rating.getValue() >= threshold && dateR.after(date))
					date = dateR;
			}

			if(date.after(recentDate))
				recentDate = date;
			lastPositivelyRatedItem = itemId;
		}

		return lastPositivelyRatedItem;
	}

	/**
	 * Returns the mean value of a list of ratings.
	 * @param ratings The list of ratings.
	 * @return The mean value.
	 */
	private int getPositiveRatingThreshold(List<Rating> ratings){
		int threshold=0;
		for(Rating r : ratings)
			threshold += r.getValue();
		return threshold/ratings.size();
	}


	/**
	 * Gets the most recently added item not rated yet (new item in the platform).
	 * @return The most recently added item not rated yet.
	 */
	public Long getLastItemAddedNotRated(){
		Long lastItemAdded = null;
		Date threshold = null;

		for(Long itemId : idao.getItemIds()){
			List<Rating> ratings = iedao.getEventsForItem(itemId, Rating.class);
			Date firstTimestamp=new Date(ratings.get(0).getTimestamp()*1000);
			if(threshold == null){
				threshold = firstTimestamp;
				lastItemAdded=itemId;
			}

			for(Rating r : ratings){
				Date timestamp = new Date(r.getTimestamp()*1000);
				if(timestamp.before(firstTimestamp))
					firstTimestamp = timestamp;
			}

			if(firstTimestamp.after(threshold)){
				threshold=firstTimestamp;
				lastItemAdded=itemId;
			}
		}

		return lastItemAdded;
	}

}