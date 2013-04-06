import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;

public class PreviousState {

	private ArrayList<Integer> peasantIds = new ArrayList<Integer>();
	private HashMap<Integer, Integer> peasantHP = new HashMap<Integer, Integer>();
	private HashMap<Integer, Point> peasantLocs = new HashMap<Integer, Point>();
	private HashMap<Integer, Boolean[][]> hasCargo = new HashMap<Integer, Boolean[][]>();
	private ArrayList<Integer> toRemove = new ArrayList<Integer>();
	private int columns;
	private int rows;
	
	public PreviousState(ArrayList<Integer> peasantIds, HashMap<Integer, Integer> peasantHP, HashMap<Integer, Point> peasantLocs, int columns, int rows) {
		for(Integer id : peasantIds) {
			this.peasantIds.add(id);
			this.peasantHP.put(id, peasantHP.get(id));
			Point point = peasantLocs.get(id);
			this.peasantLocs.put(id, new Point((int)(point.getX()), (int)(point.getY())));
			this.columns = columns;
			this.rows = rows;
			Boolean cargo[][] = new Boolean[columns][rows];
			this.hasCargo.put(id, cargo);
		}
	}
	
	/**
	 * 
	 * @param id - The id of the peasant you are concerned with.
	 * @return null if the peasant doesn't exist. The peasant's HP if it does exist.
	 */
	public Integer getPeasantHP(int id) {
		if(!peasantHP.containsKey(id)) {
			return null;
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

	public void markForRemoval(int peasantID) {
		toRemove.add(peasantID);
	}
	
	public void removeMarked() {
		for(Integer id : toRemove) {
			if(peasantIds.contains(id)) {
				peasantIds.remove(id);
			}
			if(peasantHP.containsKey(id)) {
				peasantHP.remove(id);
			}
			if(peasantLocs.containsKey(id)) {
				peasantLocs.remove(id);
			}
			if(hasCargo.containsKey(id)) {
				hasCargo.remove(id);
			}
		}
		toRemove = new ArrayList<Integer>();
	}
	
	public void addPeasant(int peasantId, int peasantHP, Point peasantLoc) {
		this.peasantIds.add(peasantId);
		this.peasantHP.put(peasantId, peasantHP);
		this.peasantLocs.put(peasantId, new Point((int)(peasantLoc.getX()), (int)(peasantLoc.getY())));
		Boolean cargo[][] = new Boolean[columns][rows];
		this.hasCargo.put(peasantId, cargo);
	}
	
	public Boolean getHasCargo(int peasantId, int x, int y) {
		return hasCargo.get(peasantId)[x][y];
	}

	public void setHasCargo(int peasantId, int x, int y, boolean value) {
		hasCargo.get(peasantId)[x][y] = value;
	}
	
}
