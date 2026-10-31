package com.slayerswaps;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/**
 * Development only: a side panel to pretend to have a slayer task, at one of its locations, by one of its teleports,
 * picked from drop-downs or at random. Lives in the test sources, so it's only in the dev client, never in Plugin Hub
 * releases. The pretend task is kept (in its own config group) until changed, so it's still there after a restart; it's
 * never written over the player's own choices.
 */
@PluginDescriptor(
	name = "Slayer Swaps Testing",
	description = "Pretend to have a slayer task, to test Slayer Swaps",
	tags = {"slayer", "test"}
)
public class SlayerSwapsTestingPlugin extends Plugin
{
	private static final String REAL_TASK = "My real task";
	private static final String KONAR = "Konar: ";
	private static final String REAL_LEVEL = "Real level";
	// Where the pretend task is kept between restarts: the drop-downs' monster, location label and teleport
	// (Routes.key), the Agility level and whether it's done
	private static final String GROUP = "slayerswapstesting";
	private static final String TASK_KEY = "task";
	private static final String LOCATION_KEY = "location";
	private static final String ROUTE_KEY = "route";
	private static final String AGILITY_KEY = "agility";
	private static final String DONE_KEY = "done";

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private ConfigManager configManager;

	private SlayerData data;
	private NavigationButton navButton;
	private final JComboBox<String> taskBox = new JComboBox<>();
	private final JComboBox<String> locationBox = new JComboBox<>();
	private final JComboBox<String> teleportBox = new JComboBox<>();
	private final JComboBox<String> agilityBox = new JComboBox<>();
	private final JCheckBox done = new JCheckBox("Task done");
	// The locations and teleports behind the drop-downs' labels, in the same order
	private final List<String> locations = new ArrayList<>();
	private final List<SlayerData.Route> routes = new ArrayList<>();
	// While the drop-downs are being filled in, so each change doesn't apply a half-chosen test
	private boolean filling;

	@Override
	protected void startUp()
	{
		data = SlayerData.load(gson);

		taskBox.addItem(REAL_TASK);
		data.getTasks().stream()
			.filter(t -> !validLocations(t).isEmpty() || t.getKonar() != null && !t.getKonar().isEmpty())
			.map(SlayerData.TaskData::getName)
			.sorted(Comparator.comparing(String::toLowerCase))
			.forEach(taskBox::addItem);
		agilityBox.addItem(REAL_LEVEL);
		for (int level = 99; level >= 1; level--)
		{
			agilityBox.addItem(String.valueOf(level));
		}
		agilityBox.setToolTipText("Pretend to have this Agility level, for teleports that need one");
		agilityBox.addActionListener(e ->
		{
			int i = agilityBox.getSelectedIndex();
			TaskOverride.agility = i <= 0 ? null : 100 - i;
			save(AGILITY_KEY, TaskOverride.agility);
			refresh();
		});
		// A location or teleport chosen on the slayer helmet during a test shows here too
		TaskOverride.changed = () -> SwingUtilities.invokeLater(this::showOverride);
		taskBox.addActionListener(e -> taskChosen());
		locationBox.addActionListener(e -> locationChosen());
		teleportBox.addActionListener(e -> apply());
		locationBox.setEnabled(false);
		teleportBox.setEnabled(false);

		JButton random = new JButton("Random task");
		random.setToolTipText("A random task, location and teleport");
		random.addActionListener(e -> randomTask());
		JButton real = new JButton("Use my real task");
		real.addActionListener(e -> taskBox.setSelectedIndex(0));
		done.setToolTipText("Pretend the task is done, to see the way back to your slayer master");
		done.addActionListener(e ->
		{
			TaskOverride.done = done.isSelected();
			save(DONE_KEY, TaskOverride.done ? true : null);
			refresh();
		});

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.add(random);
		controls.add(new JLabel("Monster"));
		controls.add(taskBox);
		controls.add(new JLabel("Location"));
		controls.add(locationBox);
		controls.add(new JLabel("Teleport"));
		controls.add(teleportBox);
		controls.add(new JLabel("Agility"));
		controls.add(agilityBox);
		controls.add(done);
		controls.add(real);

		PluginPanel panel = new PluginPanel()
		{
		};
		panel.add(controls);

		navButton = NavigationButton.builder()
			.tooltip("Slayer Swaps Testing")
			.icon(icon())
			.priority(20)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		restore();
	}

	/**
	 * Puts back the pretend task kept from before a restart.
	 */
	private void restore()
	{
		String agility = configManager.getConfiguration(GROUP, AGILITY_KEY);
		if (agility != null)
		{
			agilityBox.setSelectedItem(agility);
		}
		String task = configManager.getConfiguration(GROUP, TASK_KEY);
		if (task == null)
		{
			return;
		}
		filling = true;
		taskBox.setSelectedItem(task);
		fillLocations();
		String location = configManager.getConfiguration(GROUP, LOCATION_KEY);
		if (location != null)
		{
			locationBox.setSelectedItem(location);
		}
		fillTeleports();
		selectRoute(configManager.getConfiguration(GROUP, ROUTE_KEY));
		filling = false;
		apply();
		if (Boolean.parseBoolean(configManager.getConfiguration(GROUP, DONE_KEY)))
		{
			done.doClick();
		}
	}

	private void save(String key, Object value)
	{
		if (value == null)
		{
			configManager.unsetConfiguration(GROUP, key);
		}
		else
		{
			configManager.setConfiguration(GROUP, key, value.toString());
		}
	}

	/**
	 * Keeps the drop-downs' task, location and teleport for after a restart.
	 */
	private void saveTask()
	{
		boolean real = TaskOverride.task == null;
		save(TASK_KEY, real ? null : taskBox.getSelectedItem());
		save(LOCATION_KEY, real ? null : locationBox.getSelectedItem());
		save(ROUTE_KEY, real ? null : TaskOverride.route);
		save(DONE_KEY, null);
	}

	private void selectRoute(String key)
	{
		for (int r = 0; r < routes.size(); r++)
		{
			if (Routes.key(routes.get(r)).equals(key))
			{
				teleportBox.setSelectedIndex(r);
			}
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		TaskOverride.task = null;
		TaskOverride.location = null;
		TaskOverride.route = null;
		TaskOverride.done = false;
		TaskOverride.agility = null;
		TaskOverride.changed = null;
		refresh();
	}

	/**
	 * Picks a slayer task, one of its locations (sometimes one of Konar's areas) and one of the teleports there, and
	 * shows them in the drop-downs.
	 */
	private void randomTask()
	{
		ThreadLocalRandom random = ThreadLocalRandom.current();
		filling = true;
		taskBox.setSelectedIndex(1 + random.nextInt(taskBox.getItemCount() - 1));
		fillLocations();
		// Konar's areas a quarter of the time, when the task has any
		int konar = 0;
		for (int i = 0; i < locationBox.getItemCount(); i++)
		{
			konar += locationBox.getItemAt(i).startsWith(KONAR) ? 1 : 0;
		}
		int normal = locationBox.getItemCount() - konar;
		boolean useKonar = konar > 0 && (normal == 0 || random.nextInt(4) == 0);
		locationBox.setSelectedIndex(useKonar ? normal + random.nextInt(konar) : random.nextInt(Math.max(1, normal)));
		fillTeleports();
		if (teleportBox.getItemCount() > 0)
		{
			teleportBox.setSelectedIndex(random.nextInt(teleportBox.getItemCount()));
		}
		filling = false;
		apply();
	}

	private void taskChosen()
	{
		if (filling)
		{
			return;
		}
		filling = true;
		fillLocations();
		fillTeleports();
		filling = false;
		apply();
	}

	private void locationChosen()
	{
		if (filling)
		{
			return;
		}
		filling = true;
		fillTeleports();
		filling = false;
		apply();
	}

	private void fillLocations()
	{
		locationBox.removeAllItems();
		locations.clear();
		SlayerData.TaskData task = selectedTask();
		if (task != null)
		{
			for (String location : validLocations(task))
			{
				locations.add(location);
				locationBox.addItem(Routes.place(location));
			}
			if (task.getKonar() != null)
			{
				for (Map.Entry<String, List<String>> area : task.getKonar().entrySet())
				{
					// Her area's place with a teleport, for the teleports to list
					String place = area.getValue().stream().filter(l -> !data.routes(l).isEmpty()).findFirst().orElse(null);
					locations.add(place);
					locationBox.addItem(KONAR + area.getKey());
				}
			}
		}
		locationBox.setEnabled(task != null);
	}

	private void fillTeleports()
	{
		teleportBox.removeAllItems();
		routes.clear();
		String location = selectedLocation();
		if (location != null)
		{
			for (SlayerData.Route route : data.routes(location))
			{
				routes.add(route);
				teleportBox.addItem(Routes.describe(data, route));
			}
		}
		teleportBox.setEnabled(location != null);
	}

	/**
	 * Pretends to have what the drop-downs show.
	 */
	private void apply()
	{
		if (filling)
		{
			return;
		}
		SlayerData.TaskData task = selectedTask();
		String[] pretend = null;
		String location = null;
		SlayerData.Route route = null;
		if (task != null)
		{
			String label = (String) locationBox.getSelectedItem();
			boolean konar = label != null && label.startsWith(KONAR);
			pretend = new String[]{task.getName(), konar ? label.substring(KONAR.length()) : null};
			// Konar's area decides the location; the teleport is still picked here
			location = konar ? null : selectedLocation();
			int i = teleportBox.getSelectedIndex();
			route = i >= 0 && i < routes.size() ? routes.get(i) : null;
		}
		TaskOverride.task = pretend;
		TaskOverride.location = location;
		TaskOverride.route = route == null ? null : Routes.key(route);
		TaskOverride.done = false;
		done.setSelected(false);
		saveTask();
		refresh();
		String what = pretend == null ? "off, using your real task"
			: pretend[0] + (pretend[1] != null ? " in " + pretend[1] + " (Konar)"
				: location != null ? " at " + Routes.place(location) : "")
				+ (route != null ? " by " + Routes.describe(data, route) : "");
		String message = "Slayer Swaps test: " + what + ".";
		clientThread.invokeLater(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build()));
	}

	/**
	 * Shows the test's location and teleport after they were changed on the slayer helmet, without applying them
	 * again.
	 */
	private void showOverride()
	{
		String location = TaskOverride.location;
		String route = TaskOverride.route;
		filling = true;
		int i = location == null ? -1 : locations.indexOf(location);
		if (i >= 0)
		{
			locationBox.setSelectedIndex(i);
			fillTeleports();
		}
		selectRoute(route);
		filling = false;
		save(LOCATION_KEY, locationBox.getSelectedItem());
		save(ROUTE_KEY, route);
	}

	private SlayerData.TaskData selectedTask()
	{
		return taskBox.getSelectedIndex() <= 0 ? null : data.findTask((String) taskBox.getSelectedItem());
	}

	private String selectedLocation()
	{
		int i = locationBox.getSelectedIndex();
		return i >= 0 && i < locations.size() ? locations.get(i) : null;
	}

	/**
	 * @return a task's locations outside the Wilderness that have a teleport
	 */
	private List<String> validLocations(SlayerData.TaskData task)
	{
		List<String> out = new ArrayList<>();
		for (String location : task.getLocations())
		{
			SlayerData.LocationData info = data.location(location);
			if (info != null && !info.isWilderness() && !data.routes(location).isEmpty())
			{
				out.add(location);
			}
		}
		return out;
	}

	private static void refresh()
	{
		Runnable refresh = TaskOverride.refresh;
		if (refresh != null)
		{
			refresh.run();
		}
	}

	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.setColor(new Color(0, 200, 0));
		g.fillOval(1, 1, 14, 14);
		g.setColor(Color.BLACK);
		g.drawString("T", 5, 12);
		g.dispose();
		return image;
	}
}
