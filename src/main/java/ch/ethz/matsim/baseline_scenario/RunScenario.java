package ch.ethz.matsim.baseline_scenario;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.ModeRoutingParams;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import ch.ethz.matsim.baseline_scenario.scoring.BaselineScoringFunctionFactory;
import ch.ethz.matsim.mode_choice.mnl.BasicModeChoiceParameters;

public class RunScenario {
	static public void main(String[] args) {
		Config config = ConfigUtils.loadConfig(args[0]);
		
		config.global().setNumberOfThreads(Integer.parseInt(args[1]));
		config.qsim().setNumberOfThreads(Integer.parseInt(args[2]));
		
		UserMeeting.applyParameters(config);
		
		if (args[3].equals("old")) {
			config.controler().setOutputDirectory("simulation_old");
			UserMeeting.applyReplanningForSubtourModeChoice(config);
		} else if (args[3].equals("new")) {
			config.controler().setOutputDirectory("simulation_new");
			UserMeeting.applyReplanningForModeChoice(config);
		} else {
			throw new IllegalStateException();
		}
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		
		UserMeeting.applyModeChoice(controler);

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bindScoringFunctionFactory().to(BaselineScoringFunctionFactory.class).asEagerSingleton();
			}
		});
		
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {}
		});
		
		controler.run();
	}
}
