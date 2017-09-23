package ch.ethz.matsim.baseline_scenario.scoring;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
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
public class ScoreDistributionListener implements IterationEndsListener{
	@Inject
	Population population;

	@Inject
	OutputDirectoryHierarchy hierarchy;

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(hierarchy.getIterationFilename(event.getIteration(), "score_distribution.txt"))));

			for (Person person : population.getPersons().values()) {
				List<String> scores = person.getPlans().stream().map(p -> p.getScore().toString()).collect(Collectors.toList());
				writer.write(String.format("%s %s\n", person.getId().toString(), String.join(" ", scores)));
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
