package ch.ethz.matsim.baseline_scenario;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.PtConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ModeShareListener implements IterationEndsListener {
	@Inject
	Population population;

	@Inject
	OutputDirectoryHierarchy hierarchy;

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		try {
			Map<String, AtomicInteger> counts = new TreeMap<>();

			for (Person person : population.getPersons().values()) {
				List<String> modes = TripStructureUtils
						.getTrips(person.getSelectedPlan(),
								new StageActivityTypesImpl(PtConstants.TRANSIT_ACTIVITY_TYPE))
						.stream()
						.filter(t -> !t.getOriginActivity().getLinkId().equals(t.getDestinationActivity().getLinkId()))
						.map(t -> t.getLegsOnly().get(0).getMode()).map(m -> m.contains("transit") ? "pt" : m)
						.collect(Collectors.toList());

				for (String mode : modes) {
					if (!counts.containsKey(mode)) {
						counts.put(mode, new AtomicInteger(0));
					}

					counts.get(mode).incrementAndGet();
				}
			}

			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(hierarchy.getIterationFilename(event.getIteration(), "mode_share.txt"))));

			for (Map.Entry<String, AtomicInteger> entry : counts.entrySet()) {
				writer.write(String.format("%s %d\n", entry.getKey(), entry.getValue().get()));
				writer.flush();
			}

			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
