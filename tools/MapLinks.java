import java.io.File;
import java.util.Map;
import net.runelite.cache.WorldMapManager;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Position;

/**
 * Prints the world map's links between map areas (the blue arrows, like Prifddinas's gates or a dungeon's entrance)
 * from a copy of the game cache, one per line: "start x, y, plane, end x, y, plane", tab separated. Uses RuneLite's
 * cache library (net.runelite:cache, same version as the client) and its dependencies on the class path:
 *
 *     cp ~/.runelite/jagexcache/oldschool/LIVE/main_file_cache.* gamecache/
 *     javac -cp "$CP" MapLinks.java && java -cp "$CP:." MapLinks gamecache > map_links.tsv
 */
public class MapLinks
{
	public static void main(String[] args) throws Exception
	{
		try (Store store = new Store(new File(args[0])))
		{
			store.load();
			WorldMapManager map = new WorldMapManager(store);
			map.load();
			for (Map.Entry<Position, Position> link : map.getIntermapLinks().entrySet())
			{
				Position s = link.getKey(), e = link.getValue();
				System.out.println(s.getX() + "\t" + s.getY() + "\t" + s.getZ() + "\t" + e.getX() + "\t" + e.getY() + "\t" + e.getZ());
			}
		}
	}
}
