package edu.cornell.cs3410;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.circuit.Resetter;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.std.wiring.Clock;

import edu.cornell.cs3410.Program32;
import edu.cornell.cs3410.ProgramAssembler.Listing;
import edu.cornell.cs3410.ProgramState;
import edu.cornell.cs3410.RegisterFile32;
import edu.cornell.cs3410.RegisterData;
import static edu.cornell.cs3410.RegisterUtils.NUM_REGISTERS;

/**
 * MipsTest, an automated tester for MIPS processors.
 *
 * @author Peter Tseng (edited later by Favian Contreras)
 *
 */
public class MipsTest {

    private static final int TABLEA_TESTS_USED = 24; // Doesn't (and shouldn't) include table-b nops
    private static final int TABLEA_TOTAL_TESTS = 25; // Total number of tests in table-a folder
    private static final int NONUSED_TABLEB_TESTS = 3; // Includes RAM forward test
    
    private static final BitWidth REG_WIDTH = BitWidth.create(32);
    // In general, this looks like:
    // ## desc = a cool description
    // ## expect[10] = 0x1337
    private static final Pattern CMD_PATTERN = Pattern.compile(
        "^##\\s*(\\w+)(\\[([0-9]+)\\])?\\s*=\\s*(.*\\S)\\s*$"
    );

    public static int runTest(String[] args, TestCircuit circ, int i, boolean print) throws LoadFailedException, IOException {
        // If there ever comes a day when these are not inited to all 0's,
        // these two lines will need to be changed.
        int[] starting = new int[32];
        int[] expected = new int[32];

        String description = null;
        int cycles = 0;

        // Read test file, parsing out special directives along the way.
        BufferedReader in = new BufferedReader(new FileReader(args[i]));
        String line;
        StringBuffer buf = new StringBuffer();
        while ((line = in.readLine()) != null) {
            buf.append(line+"\n");
            Matcher matcher = CMD_PATTERN.matcher(line);
            if (matcher.find()) {
                String cmd = matcher.group(1).toLowerCase();
                String idx = matcher.group(3);
                String val = matcher.group(4);
                if (cmd.startsWith("desc")) {
                    description = val;
                }
                else if (cmd.startsWith("cycles")) {
                    cycles = Integer.parseInt(val);
                }
                else if (cmd.startsWith("expect")) {
                    expected[Integer.parseInt(idx)] = parse(val);
                }
                else if (cmd.startsWith("start")) {
                    starting[Integer.parseInt(idx)] = parse(val);
                }
                else if (cmd.startsWith("init")) {
                    starting[Integer.parseInt(idx)] = parse(val);
                }
                else {
                    String err = "Unrecognized directive " + cmd;
                    throw new IllegalArgumentException(err);
                }
            }
        }

        if (description == null) {
            throw new IllegalArgumentException("No description");
        }
        if (cycles <= 0) {
            throw new IllegalArgumentException("No cycle count");
        }

        int[] actual = null;
        try {
            actual = circ.runTest(buf.toString(), cycles, starting);
        }
        catch (Exception e) {
            // Why doesn't this exit normally?
            e.printStackTrace();
            System.exit(-1);
        }

        int errorsHere;
        // Hailstone testing, must suppress other register values outside v0
        if (args[args.length - 1].equals("Hailstone")){
            errorsHere = (expected[2] == actual[2] ? 0 : 1);

            System.out.printf("[%s] %s\n", count(errorsHere), description);
            if (errorsHere == 1) {
                printDiffs(expected, actual);
            }
            if (i == args.length - 2) {
                System.exit(0);
            }
        }
        // Circuit testing
        else{
            errorsHere = countDiffs(expected, actual);
            
            if (print) {
                System.out.printf("[%s] %s\n", count(errorsHere), description);
                printDiffs(expected, actual);
            }
        }
        
        return errorsHere;
    }

    /**
     * Runs tests.
     *
     * @param args First argument = .circ file, other arguments = test files.
     */
    public static void main(String[] args) throws LoadFailedException, IOException {
        if (args.length == 0) {
            System.out.println("usage: MipsTest <circuit> [test-files]...");
        }

        Project proj = ProjectActions.doOpenNoWindow(null, new File(args[0]));

        TestCircuit circ = null;
        try {
            circ = new TestCircuit(proj);
        }
        catch (Exception e) {
            // Why doesn't this exit normally?
            e.printStackTrace();
            System.exit(-1);
        }
        
        int errors = 0;
        int p1testsPassed = 0;
        int errorsHere = 0;
        int prelim_or_real = (args.length == 2 ? 1 : TABLEA_TESTS_USED);

        for (int i = 1; i <= prelim_or_real; ++i){
            errorsHere = runTest(args, circ, i, true);
            errors += errorsHere;
            p1testsPassed += (errorsHere == 0 ? 1 : 0);
        }

        // Preliminary tests
        if (args.length == 2){
            System.out.println("TOTAL: " + count(errors));
            System.out.printf("Tests with no errors: %d/%d\n", p1testsPassed, 1);
        }
        // If testing project 1, the plus 2 is to include the table-b nops
        else if (args.length <= TABLEA_TOTAL_TESTS + 2){
            errorsHere = runTest(args, circ, args.length-1, true);
            errors += errorsHere;
            p1testsPassed += (errorsHere == 0 ? 1 : 0);

            System.out.println("TOTAL: " + count(errors));
            System.out.printf("Tests with no errors: %d/%d\n", p1testsPassed, TABLEA_TESTS_USED + 1);
        } 
        else {
            int p2testsPassed = 0;
            
            for (int i = TABLEA_TOTAL_TESTS + 1; i < (args.length - NONUSED_TABLEB_TESTS); ++i){
                errorsHere = runTest(args, circ, i, true);
                errors += errorsHere;
                p2testsPassed += (errorsHere == 0 ? 1 : 0);
            }
            
            // RAM forward test
            errorsHere = runTest(args, circ, args.length - NONUSED_TABLEB_TESTS, false);
            if (errorsHere > 0) {
                System.out.println("May have forwarded output of RAM in MEM stage.");
            }

            int p2test_num = args.length - TABLEA_TOTAL_TESTS - NONUSED_TABLEB_TESTS - 1;

            System.out.println("\nTOTAL: " + count(errors));
            System.out.printf("Table A tests with no errors: %d/%d\n", p1testsPassed, TABLEA_TESTS_USED);
            System.out.printf("Table B tests with no errors: %d/%d\n", p2testsPassed, p2test_num);
        } 
        // Why doesn't this quit normally?
        System.exit(0);
    }

    /**
     * Holds info about a circuit to be tested.
     */
    private static class TestCircuit {
        private final CircuitState state;
        private final InstanceState registerFileState;
        private final InstanceState programRomState;

        /**
         * Constructs a TestCircuit.
         *
         * @param proj Project file of circuit
         * @throws IllegalArgumentException if there is not exactly one register file or program ROM
         */
        private TestCircuit(Project proj) {
            // We need to use their main processor's CircuitState.
            // Try in this order: MIPS32, MIPS, or main circuit.
            // Ask students to submit their processors with this naming!
            Circuit main;
            Circuit mips = proj.getLogisimFile().getCircuit("MIPS");
            Circuit mips32 = proj.getLogisimFile().getCircuit("MIPS32");

            // What, why do they have both?
            if (mips != null && mips32 != null) {
                throw new IllegalArgumentException("Had both MIPS and MIPS32");
            }

            if (mips32 != null) {
                state = proj.getCircuitState(mips32);
                main = mips32;
                proj.setCurrentCircuit(mips32);
            }
            else if (mips != null) {
                state = proj.getCircuitState(mips);
                main = mips;
                proj.setCurrentCircuit(mips);
            }
            else {
                System.err.println("Warning: no MIPS or MIPS32");
                state = proj.getCircuitState();
                main = proj.getCurrentCircuit();
            }


            Circuit registerCircuit = null;
            Component registerFile = null;
            Component programRom = null;
            boolean haveClock = false;

            for (Circuit c : proj.getLogisimFile().getCircuits()) {
                for (Component x : c.getNonWires()) {
                    if (x.getFactory() instanceof RegisterFile32) {
                        if (registerFile != null) {
                            throw new IllegalArgumentException("More than one register file");
                        }
                        registerCircuit = c;
                        registerFile = x;
                    }
                    else if (x.getFactory() instanceof Program32) {
                        if (programRom != null) {
                            throw new IllegalArgumentException("More than one program ROM");
                        }
                        programRom = x;
                    }
                    else if (x.getFactory() instanceof Clock) {
                        haveClock = true;
                    }
                }
            }

            if (registerFile == null) {
                throw new IllegalArgumentException("No register file");
            }
            if (programRom == null) {
                throw new IllegalArgumentException("No program ROM");
            }
            if (!haveClock) {
                throw new IllegalArgumentException("No clock");
            }

            // If their register file is tucked away in a subcircuit,
            // we need to do some legwork to get the right InstanceState for it.
            if (registerCircuit == main) {
                registerFileState = state.getInstanceState(registerFile);
            }
            else {
                // We're going to hope it's only nested one deep?
                CircuitState substate = null;
                for (Component x : main.getNonWires()) {
                    // Is there a better way of checking this? Looks fragile.
                    if (x.getFactory().getName().equals(registerCircuit.getName())) {
                        substate = registerCircuit.getSubcircuitFactory().getSubstate(state, x);
                    }
                }

                if (substate == null) {
                    throw new IllegalArgumentException("Register file nested too deep or not actually in circuit?");
                }

                registerFileState = substate.getInstanceState(registerFile);
            }

            programRomState = state.getInstanceState(programRom);
        }

        /**
         * Runs a test on this circuit.
         *
         * @param program Test code
         * @param cycles Number of cycles to run for
         * @param starting Array of starting values for register file
         * @return Array of values in the register file after running test
         */
        private int[] runTest(String program, int cycles, int[] starting) throws IOException {
            Resetter.reset(state);
            setCode(programRomState, program);

            if (starting != null) {
                setRegisters(registerFileState, starting);
            }

            Propagator prop = state.getPropagator();

            // Initial propagate deals with people who have their PC register
            // updating on the opposite edge.
            prop.propagate();

            // Two ticks make one cycle, so go to cycles * 2
            // Add one more half-cycle for falling edge people
            for (int i = 0; i < cycles * 2 + 1; ++i) {
                prop.tick();
                prop.propagate();
            }

            return getRegisters(registerFileState);
        }
    }

    /**
     * Checks that the array is of the right length.
     *
     * @throws IllegalArgumentException if the array is the wrong length.
     */
    private static void checkLength(int[] array, String name, int expected) {
        if (array.length == expected) {
            return;
        }
        String format = "%s is length %d, but it should be length %d";
        String err = String.format(format, name, array.length, expected);
        throw new IllegalArgumentException(err);
    }

    /**
     * @return the number of differences between the two arrays.
     */
    private static int countDiffs(int[] expected, int[] actual) {
        checkLength(expected, "Expected array", NUM_REGISTERS);
        checkLength(actual, "Actual array", NUM_REGISTERS);
        int errors = 0;
        for (int i = 0; i < NUM_REGISTERS; ++i) {
            if (expected[i] != actual[i]) {
                ++errors;
            }
        }
        return errors;
    }

    /**
     * Prints to standard output the differences between the two arrays.
     */
    private static void printDiffs(int[] expected, int[] actual) {
        checkLength(expected, "Expected array", NUM_REGISTERS);
        checkLength(actual, "Actual array", NUM_REGISTERS);
        for (int i = 0; i < NUM_REGISTERS; ++i) {
            if (expected[i] != actual[i]) {
                System.out.printf("    Error in register %d. Expected 0x%08x, but got 0x%08x.\n", i, expected[i], actual[i]);
            }
        }
    }

    /**
     * Changes a Program ROM's state so that it contains the specified code.
     *
     * @param state Program ROM's InstanceState
     * @param program Code to load
     */
    private static void setCode(InstanceState state, String program) throws IOException {
        Listing code = state.getAttributeValue(Program32.CONTENTS_ATTR);
        code.setSource(program);
    }

    /**
     * Changes a Register File's state so that it contains the specified values.
     *
     * @param state Register File's InstanceState
     * @param vals Values to set
     */
    private static void setRegisters(InstanceState state, int[] vals) {
        checkLength(vals, "Values array", NUM_REGISTERS);

        RegisterData data = RegisterData.get(state);
        for (int i = 1; i < NUM_REGISTERS; ++i) {
            data.regs[i] = Value.createKnown(REG_WIDTH, vals[i]);
        }
    }

    /**
     * @param state InstanceState of a register file.
     * @return Array of the values in the register file.
     */
    private static int[] getRegisters(InstanceState state) {
        RegisterData data = RegisterData.get(state);
        int[] regs = new int[NUM_REGISTERS];
        for (int i = 0; i < NUM_REGISTERS; ++i) {
            if (!data.regs[i].isFullyDefined()) {
                String err = String.format("Register %d undefined", i);
                throw new IllegalStateException(err);
            }
            regs[i] = data.regs[i].toIntValue();
        }
        return regs;
    }

    /**
     * Parses a string which could be a base 10 or base 16 number.
     * @return the number
     */
    private static int parse(String x) {
        if (x.toLowerCase().startsWith("0x")) {
            // 0x and 8 hexadecimal digits is the max. If longer, fail!
            if (x.length() > 10) {
                throw new IllegalArgumentException(x + " is out of range");
            }

            // Need to use a long here, otherwise 0x8000000 - 0xffffffff fail!
            long l = Long.parseLong(x.substring(2), 16);
            if (l > Integer.MAX_VALUE) {
                l -= (1L << 32);
            }
            return (int) l;
        }
        return Integer.parseInt(x);
    }

    /**
     * @return "1 error" if there is 1 error, otherwise "x errors" for x errors
     */
    private static String count(int errors) {
        if (errors == 1) {
            return " 1 error ";
        }
        return String.format(" %d errors ", errors);
    }

    /**
     * @return Aesthetically pleasing score with at most 1 floating point.
     */
    private static String niceScore(double score, double max_score) {
        StringBuilder str = new StringBuilder();
        if (score % 1 == 0) {
            str.append(String.format("%.0f/", score));
        }
        else {
            str.append(String.format("%.1f/", score));
        }
        if (max_score % 1 == 0) {
            str.append(String.format("%.0f\n", max_score));
            return str.toString();
        }
        else {
            str.append(String.format("%.1f\n", max_score));
            return str.toString();
        }
    }
}




















