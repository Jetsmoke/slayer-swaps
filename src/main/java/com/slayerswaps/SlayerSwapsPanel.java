package com.slayerswaps;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * Side panel for choosing, per task, which location to go to and which teleport to use.
 * Swing code; runs on the event dispatch thread.
 */
class SlayerSwapsPanel extends PluginPanel
{
	static final String AUTO = "Best teleport I'm carrying";

	interface Choices
	{
		String location(SlayerData.TaskData task);

		String routeKey(SlayerData.TaskData task);

		List<String> locations(SlayerData.TaskData task);

		List<SlayerData.Route> routes(String location);

		void choose(SlayerData.TaskData task, String location, String routeKey);

		boolean isEnabled(SlayerData.TaskData task);

		void setEnabled(SlayerData.TaskData task, boolean enabled);
	}

	private final SlayerData data;
	private final Choices choices;
	private final JLabel current = new JLabel();
	private final JPanel currentRow = new JPanel(new BorderLayout());
	private final JPanel list = new JPanel();
	private final List<TaskRow> rows = new ArrayList<>();

	SlayerSwapsPanel(SlayerData data, Choices choices)
	{
		this.data = data;
		this.choices = choices;
		setLayout(new BorderLayout(0, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Slayer Swaps");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title);
		top.add(Box.createVerticalStrut(6));
		current.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		top.add(current);
		top.add(Box.createVerticalStrut(4));
		top.add(currentRow);
		top.add(Box.createVerticalStrut(8));

		IconTextField search = new IconTextField();
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				filter(search.getText());
			}

			public void removeUpdate(DocumentEvent e)
			{
				filter(search.getText());
			}

			public void changedUpdate(DocumentEvent e)
			{
				filter(search.getText());
			}
		});
		top.add(search);
		add(top, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		List<SlayerData.TaskData> tasks = new ArrayList<>(data.getTasks());
		tasks.sort(Comparator.comparing(t -> t.getName().toLowerCase()));
		for (SlayerData.TaskData task : tasks)
		{
			if (choices.locations(task).isEmpty())
			{
				continue;
			}
			TaskRow row = new TaskRow(task);
			rows.add(row);
			list.add(row);
		}
		add(list, BorderLayout.CENTER);
		setCurrentTask(null, 0);
	}

	void setCurrentTask(SlayerData.TaskData task, int remaining)
	{
		currentRow.removeAll();
		if (task == null)
		{
			current.setText("No slayer task");
		}
		else
		{
			current.setText("Current task: " + task.getName() + " (" + remaining + " left)");
			currentRow.add(new TaskRow(task), BorderLayout.CENTER);
		}
		currentRow.revalidate();
		currentRow.repaint();
	}

	void refresh()
	{
		for (TaskRow row : rows)
		{
			row.load();
		}
	}

	private void filter(String text)
	{
		String q = text.trim().toLowerCase();
		for (TaskRow row : rows)
		{
			row.setVisible(q.isEmpty() || row.task.getName().toLowerCase().contains(q)
				|| row.task.getLocations().stream().anyMatch(l -> l.toLowerCase().contains(q)));
		}
		list.revalidate();
	}

	private class TaskRow extends JPanel
	{
		private final SlayerData.TaskData task;
		private final JComboBox<String> location = new JComboBox<>();
		private final JComboBox<RouteItem> route = new JComboBox<>();
		private final JCheckBox enabled;
		private boolean loading;

		TaskRow(SlayerData.TaskData task)
		{
			this.task = task;
			this.enabled = new JCheckBox(task.getName());
			enabled.setForeground(Color.WHITE);
			setLayout(new GridLayout(3, 1, 0, 2));
			setBorder(BorderFactory.createEmptyBorder(4, 0, 6, 0));
			enabled.setToolTipText("Untick to never swap or highlight teleports for this task");
			add(enabled);
			add(location);
			add(route);
			enabled.addActionListener(e ->
			{
				if (!loading)
				{
					choices.setEnabled(task, enabled.isSelected());
					updateEnabled();
				}
			});
			location.addActionListener(e ->
			{
				if (!loading)
				{
					fillRoutes(null);
					save();
				}
			});
			route.addActionListener(e ->
			{
				if (!loading)
				{
					save();
				}
			});
			load();
		}

		void load()
		{
			loading = true;
			location.removeAllItems();
			for (String l : choices.locations(task))
			{
				location.addItem(l);
			}
			location.setSelectedItem(choices.location(task));
			fillRoutes(choices.routeKey(task));
			enabled.setSelected(choices.isEnabled(task));
			updateEnabled();
			loading = false;
		}

		private void updateEnabled()
		{
			location.setEnabled(enabled.isSelected());
			route.setEnabled(enabled.isSelected());
		}

		private void fillRoutes(String selectedKey)
		{
			boolean wasLoading = loading;
			loading = true;
			route.removeAllItems();
			route.addItem(new RouteItem(null, AUTO));
			String loc = (String) location.getSelectedItem();
			if (loc != null)
			{
				for (SlayerData.Route r : choices.routes(loc))
				{
					RouteItem item = new RouteItem(Routes.key(r), Routes.describe(data, r));
					route.addItem(item);
					if (item.key.equals(selectedKey))
					{
						route.setSelectedItem(item);
					}
				}
			}
			loading = wasLoading;
		}

		private void save()
		{
			RouteItem r = (RouteItem) route.getSelectedItem();
			choices.choose(task, (String) location.getSelectedItem(), r == null ? null : r.key);
		}
	}

	private static class RouteItem
	{
		private final String key;
		private final String text;

		RouteItem(String key, String text)
		{
			this.key = key;
			this.text = text;
		}

		@Override
		public String toString()
		{
			return text;
		}
	}
}
