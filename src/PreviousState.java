import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;


public class PreviousState {

	private ArrayList<Integer> peasantIds = new ArrayList<Integer>();
	private HashMap<Integer, Integer> peasantHP = new HashMap<Integer, Integer>();
	private HashMap<Integer, Point> peasantLocs = new HashMap<Integer, Point>();
	
	@SuppressWarnings("unchecked")
	public PreviousState(ArrayList<Integer> peasantIds, HashMap<Integer, Integer> peasantHP, HashMap<Integer, Point> peasantLocs) {
		for(Integer id : peasantIds) {
			this.peasantIds.add(id);
			this.peasantHP.put(id, peasantHP.get(id));
			this.peasantLocs.put(id, peasantLocs.get(id));
		}
	}
	
	/**
	 * 
	 * @param id - The id of the peasant you are concerned with.
	 * @return -1 if the peasant doesn't exist. The peasant's HP if it does exist.
	 */
	public int getPeasantHP(int id) {
		if(!peasantHP.containsKey(id)) {
			return -1;
		}
		return peasantHP.get(id);
	}
	
	/**
	 * 
	 * @param id - ID of the peasant whose HP has changed.
	 * @param HP - The new HP.
	 */
	public void setPeasantHP(int id, int HP) {
		peasantHP.remove(id);
		peasantHP.put(id, HP);
	}
	
	public Point getPeasantLoc(int id) {
		return peasantLocs.get(id);
	}

	public ArrayList<Integer> getPeasantIds() {
		return peasantIds;
	}
	
}
