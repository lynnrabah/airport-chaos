import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

//Entry point of the program. Main knows nothing about the airport:
//it only reads text lines and hands each one to CommandProcessor.execute(line),
//then prints whatever execute returns.
//  java -cp out Main                       -> interactive numbered menu
//  java -cp out Main scripts/scenarioA.txt -> batch mode (runs a command file)

public class Main {

	public static void main(String[] args) {
		CommandProcessor cp=new CommandProcessor();
		if (args.length>=1) {
			runBatch(cp,args[0]);
		}
		else {
			menu(cp);
		}
	}

	//BATCH MODE: read the script line by line, skip blank lines and comments (#),
	//echo each command so the output shows what was run, then print its result
	static void runBatch(CommandProcessor cp,String path) {
		try (Scanner reader=new Scanner(new File(path))) {
			int lineNo=0;
			while (reader.hasNextLine()) {
				lineNo++;
				String line=reader.nextLine().trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				System.out.println("> "+line);
				System.out.println(cp.execute(line));
				System.out.println();
			}
			System.out.println("Batch finished: "+lineNo+" line(s) read from "+path);
		}
		catch (FileNotFoundException e) {
			System.out.println("ERROR: script file not found: "+path);
		}
	}

	//INTERACTIVE MODE: show the numbered menu, ask the follow-up questions,
	//build ONE command string and send it to the same execute method as batch mode
	static void menu(CommandProcessor cp) {
		Scanner in=new Scanner(System.in);
		while (true) {
			System.out.println();
			System.out.println("=============== AIRPORT CHAOS ===============");
			System.out.println(" 1) LOAD             2) LIST_RUNWAY      3) FIND");
			System.out.println(" 4) ADD              5) CANCEL           6) RESCHEDULE");
			System.out.println(" 7) CHECK_CONFLICT   8) RUNWAY_CLOSURE   9) EMERGENCY_INSERT");
			System.out.println("10) MASS_CANCEL     11) SORT_PRIORITY   12) SORT_ID");
			System.out.println("13) ANALYZE         14) BENCHMARK       15) STATS");
			System.out.println("16) TRACE ON/OFF    17) MATRIX          18) PERFECT_HASH");
			System.out.println("19) HELP            20) Type a raw command");
			System.out.println(" 0) Exit");
			System.out.print("Choice: ");
			if (!in.hasNextLine()) {
				return; //input closed (e.g. Ctrl+D): leave instead of crashing
			}
			String choice=in.nextLine().trim();
			String cmd=null;
			switch (choice) {
				case "0":
					System.out.println("Goodbye.");
					return;
				case "1":
					String path=ask(in,"CSV file path (Enter = data/airport_flights.csv)");
					if (path.isEmpty()) {
						path="data/airport_flights.csv";
					}
					cmd="LOAD "+quote(path);
					break;
				case "2":
					cmd="LIST_RUNWAY "+ask(in,"Runway (R1..R5)");
					break;
				case "3":
					cmd="FIND "+ask(in,"Flight ID");
					break;
				case "4":
					cmd="ADD "+flightArguments(in);
					break;
				case "5":
					cmd="CANCEL "+ask(in,"Flight ID");
					break;
				case "6":
					cmd="RESCHEDULE "+ask(in,"Flight ID")+" "+quote(ask(in,"New start (YYYY-MM-DD HH:MM)"));
					break;
				case "7":
					cmd="CHECK_CONFLICT "+ask(in,"Runway (R1..R5)")+" "
						+quote(ask(in,"Start (YYYY-MM-DD HH:MM)"))+" "
						+quote(ask(in,"End (YYYY-MM-DD HH:MM)"));
					break;
				case "8":
					cmd="RUNWAY_CLOSURE "+ask(in,"Runway (R1..R5)")+" "
						+quote(ask(in,"Closure from (YYYY-MM-DD HH:MM)"))+" "
						+quote(ask(in,"Closure to (YYYY-MM-DD HH:MM)"));
					break;
				case "9":
					cmd="EMERGENCY_INSERT "+flightArguments(in);
					break;
				case "10":
					cmd="MASS_CANCEL "+quote(ask(in,"Window from (YYYY-MM-DD HH:MM)"))+" "
						+quote(ask(in,"Window to (YYYY-MM-DD HH:MM)"));
					break;
				case "11":
					cmd="SORT_PRIORITY";
					break;
				case "12":
					cmd="SORT_ID";
					break;
				case "13":
					cmd="ANALYZE";
					break;
				case "14":
					cmd="BENCHMARK";
					break;
				case "15":
					cmd="STATS";
					break;
				case "16":
					cmd="TRACE "+ask(in,"ON or OFF");
					break;
				case "17":
					cmd="MATRIX";
					break;
				case "18":
					cmd="PERFECT_HASH";
					break;
				case "19":
					cmd="HELP";
					break;
				case "20":
					cmd=ask(in,"Command");
					break;
				default:
					System.out.println("Invalid choice, type a number from the menu.");
					continue;
			}
			System.out.println(cp.execute(cmd));
		}
	}

	//Asks the 8 values that ADD and EMERGENCY_INSERT need, in the order the command expects
	static String flightArguments(Scanner in) {
		StringBuilder s=new StringBuilder();
		s.append(ask(in,"Flight ID")).append(" ");
		s.append(ask(in,"Airline")).append(" ");
		s.append(ask(in,"Origin airport")).append(" ");
		s.append(ask(in,"Destination airport")).append(" ");
		s.append(ask(in,"Runway (R1..R5)")).append(" ");
		s.append(quote(ask(in,"Start (YYYY-MM-DD HH:MM)"))).append(" ");
		s.append(quote(ask(in,"End (YYYY-MM-DD HH:MM)"))).append(" ");
		s.append(ask(in,"Priority (1, 2 or 3)"));
		return s.toString();
	}

	//Prints the question and returns the answer without surrounding spaces
	static String ask(Scanner in,String prompt) {
		System.out.print(prompt+": ");
		if (!in.hasNextLine()) {
			return "";
		}
		return in.nextLine().trim();
	}

	//Timestamps contain a space, so the menu wraps them in quotes for the Tokenizer.
	//If the user already typed the quotes, they are not added twice.
	static String quote(String s) {
		if (s.startsWith("\"") && s.endsWith("\"") && s.length()>=2) {
			return s;
		}
		return "\""+s+"\"";
	}
}
