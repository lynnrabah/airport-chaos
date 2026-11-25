import java.util.ArrayList;

public class CommandProcessor {
//    private Scheduler sched;
//    private CrisisEngine crisis;
    private boolean trace;
    private boolean loaded;

    public CommandProcessor() {
//        this.sched=new Scheduler();
//        this.crisis=new CrisisEngine(sched);
        this.trace=false;
        this.loaded=false;
    }
    
    public String doHelp(ArrayList<String> tokenized) {
        if (tokenized.size() != 1) return "ERROR: Usage: HELP";
        return """
        Commands:
          LOAD <file>
          LIST_RUNWAY <R1..R5>
          FIND <flight_id>
          ADD <id> <airline> <origin> <dest> <runway> "<start>" "<end>" <priority>
          CANCEL <flight_id>
          RESCHEDULE <flight_id> "<new start>"
          CHECK_CONFLICT <runway> "<start>" "<end>"
          RUNWAY_CLOSURE <runway> "<from>" "<to>"
          EMERGENCY_INSERT <id> <airline> <origin> <dest> <runway> "<start>" "<end>" <priority>
          MASS_CANCEL "<from>" "<to>"
          SORT_PRIORITY
          SORT_ID
          ANALYZE
          BENCHMARK
          STATS
          TRACE ON|OFF
          HELP
          MATRIX
          PERFECT_HASH

        Times are written in quotes as "YYYY-MM-DD HH:MM", e.g. "2026-11-27 09:00".
        Run LOAD <file> first: every command except LOAD, HELP and TRACE needs a loaded file.
        """;
    }

    public String doTrace(ArrayList<String> tokenized) {
        if (tokenized.size()!=2) return "ERROR: Usage: TRACE ON|OFF";
        String t=tokenized.get(1).toUpperCase();
        if (t.equals("ON")) {
            this.trace = true;
            return "Trace on";
        }
        else if (t.equals("OFF")) {
            this.trace = false;
            return "Trace off";
        }
        else {
            return "ERROR: Usage: TRACE ON|OFF";
        }
    }

    public String doLoad(ArrayList<String> tokenized) {
        if (tokenized.size()!=2) return "ERROR: Usage: LOAD <file>";
        
        return "ERROR: LOAD is not implemented yet";
    }

    public String doListRunway(ArrayList<String> tokenized) {
        if (tokenized.size()!=2) return "ERROR: Usage: LIST_RUNWAY <R1..R5>";
        
        return "ERROR: LIST_RUNWAY is not implemented yet";
    }

    public String doFind(ArrayList<String> tokenized) {
        if (tokenized.size()!=2) return "ERROR: Usage: FIND <flight_id>";
        
        return "ERROR: FIND is not implemented yet";
    }

    public String doAdd(ArrayList<String> tokenized) {
        if (tokenized.size()!=9) return "ERROR: Usage: ADD <id> <airline> <origin> <dest> <runway> \"<start>\" \"<end>\" <priority>";
        
        return "ERROR: ADD is not implemented yet";
    }

    public String doCancel(ArrayList<String> tokenized) {
        if (tokenized.size()!=2) return "ERROR: Usage: CANCEL <flight_id>";
        
        return "ERROR: CANCEL is not implemented yet";
    }

    public String doReschedule(ArrayList<String> tokenized) {
        if (tokenized.size()!=3) return "ERROR: Usage: RESCHEDULE <flight_id> \"<new start>\"";
        
        return "ERROR: RESCHEDULE is not implemented yet";
    }

    public String doCheckConflict(ArrayList<String> tokenized) {
        if (tokenized.size()!=4) return "ERROR: Usage: CHECK_CONFLICT <runway> \"<start>\" \"<end>\"";
        
        return "ERROR: CHECK_CONFLICT is not implemented yet";
    }

    public String doRunwayClosure(ArrayList<String> tokenized) {
        if (tokenized.size()!=4) return "ERROR: Usage: RUNWAY_CLOSURE <runway> \"<from>\" \"<to>\"";
        
        return "ERROR: RUNWAY_CLOSURE is not implemented yet";
    }

    public String doEmergencyInsert(ArrayList<String> tokenized) {
        if (tokenized.size()!=9) return "ERROR: Usage: EMERGENCY_INSERT <id> <airline> <origin> <dest> <runway> \"<start>\" \"<end>\" <priority>";
        
        return "ERROR: EMERGENCY_INSERT is not implemented yet";
    }

    public String doMassCancel(ArrayList<String> tokenized) {
        if (tokenized.size()!=3) return "ERROR: Usage: MASS_CANCEL \"<from>\" \"<to>\"";
        
        return "ERROR: MASS_CANCEL is not implemented yet";
    }

    public String doSortPriority(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: SORT_PRIORITY";
        
        return "ERROR: SORT_PRIORITY is not implemented yet";
    }

    public String doSortId(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: SORT_ID";
        
        return "ERROR: SORT_ID is not implemented yet";
    }

    public String doAnalyze(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: ANALYZE";
        
        return "ERROR: ANALYZE is not implemented yet";
    }

    public String doBenchmark(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: BENCHMARK";
        
        return "ERROR: BENCHMARK is not implemented yet";
    }

    public String doStats(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: STATS";
        
        return "ERROR: STATS is not implemented yet";
    }

    public  String doMatrix(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: MATRIX";
        
        return "ERROR: MATRIX is not implemented yet";
    }

    public String doPerfectHash(ArrayList<String> tokenized) {
        if (tokenized.size()!=1) return "ERROR: Usage: PERFECT_HASH";
        
        return "ERROR: PERFECT_HASH is not implemented yet";
    }
    
    public String execute(String line) {
        try {
            ArrayList<String> tokenized=Tokenizer.tokenize(line);
            if (tokenized.isEmpty()) {
                return "";
            }
            String command=tokenized.get(0).toUpperCase();

            if (!command.equals("LOAD") && !command.equals("HELP") && !command.equals("TRACE") && !loaded) {
                return "ERROR: run LOAD <file> first.";
            }

            switch (command) {
                case "LOAD":             return doLoad(tokenized);
                case "LIST_RUNWAY":      return doListRunway(tokenized);
                case "FIND":             return doFind(tokenized);
                case "ADD":              return doAdd(tokenized);
                case "CANCEL":           return doCancel(tokenized);
                case "RESCHEDULE":       return doReschedule(tokenized);
                case "CHECK_CONFLICT":   return doCheckConflict(tokenized);
                case "RUNWAY_CLOSURE":   return doRunwayClosure(tokenized);
                case "EMERGENCY_INSERT": return doEmergencyInsert(tokenized);
                case "MASS_CANCEL":      return doMassCancel(tokenized);
                case "SORT_PRIORITY":    return doSortPriority(tokenized);
                case "SORT_ID":          return doSortId(tokenized);
                case "ANALYZE":          return doAnalyze(tokenized);
                case "BENCHMARK":        return doBenchmark(tokenized);
                case "STATS":            return doStats(tokenized);
                case "TRACE":            return doTrace(tokenized);
                case "HELP":             return doHelp(tokenized);
                case "MATRIX":           return doMatrix(tokenized);
                case "PERFECT_HASH":     return doPerfectHash(tokenized);
            }

            return "ERROR: unknown command '"+command+"'. Type HELP.";

        }
        catch (IllegalArgumentException e) {
            return "ERROR: "+e.getMessage();
        }
        catch (Exception e) {
            return "ERROR: "+e;
        }
    }
    
}