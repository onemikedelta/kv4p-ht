/*
kv4p HT (see http://kv4p.com)
Copyright (C) 2026 Vance Vagell

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.vagell.kv4pht.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ChannelMemoryCsvTest {

    private static ChannelMemory memory(String name, String frequency, int offset, int offsetKhz,
                                        String txTone, String rxTone, String group, boolean skip) {
        ChannelMemory memory = new ChannelMemory();
        memory.name = name;
        memory.frequency = frequency;
        memory.offset = offset;
        memory.offsetKhz = offsetKhz;
        memory.txTone = txTone;
        memory.rxTone = rxTone;
        memory.group = group;
        memory.skipDuringScan = skip;
        return memory;
    }

    @Test
    public void exportThenImportPreservesEveryField() {
        ChannelMemory original = memory("Repeater 1", "145.6000", ChannelMemory.OFFSET_DOWN, 600,
            "82.5", "None", "Local", true);

        List<ChannelMemory> imported = ChannelMemoryCsv
            .fromCsv(ChannelMemoryCsv.toCsv(List.of(original)))
            .getMemories();

        assertEquals(1, imported.size());
        ChannelMemory result = imported.get(0);
        assertEquals("Repeater 1", result.name);
        assertEquals("145.6000", result.frequency);
        assertEquals(ChannelMemory.OFFSET_DOWN, result.offset);
        assertEquals(600, result.offsetKhz);
        assertEquals("82.5", result.txTone);
        assertEquals(ChannelMemoryCsv.NONE, result.rxTone);
        assertEquals("Local", result.group);
        assertTrue(result.skipDuringScan);
    }

    @Test
    public void quotesFieldsHoldingSeparatorsAndReadsThemBack() {
        ChannelMemory original = memory("Hill, \"top\"", "146.5200", ChannelMemory.OFFSET_NONE, 600,
            "None", "None", "Group, two", false);

        String csv = ChannelMemoryCsv.toCsv(List.of(original));
        assertTrue(csv.contains("\"Hill, \"\"top\"\"\""));

        ChannelMemory result = ChannelMemoryCsv.fromCsv(csv).getMemories().get(0);
        assertEquals("Hill, \"top\"", result.name);
        assertEquals("Group, two", result.group);
    }

    @Test
    public void readsColumnsByHeaderRegardlessOfOrder() {
        String csv = "frequency,name,offset\r\n145.5000,Simplex,Up\r\n";

        ChannelMemory result = ChannelMemoryCsv.fromCsv(csv).getMemories().get(0);

        assertEquals("Simplex", result.name);
        assertEquals("145.5000", result.frequency);
        assertEquals(ChannelMemory.OFFSET_UP, result.offset);
        // Columns the file leaves out fall back to the values the app treats as unset.
        assertEquals(600, result.offsetKhz);
        assertEquals(ChannelMemoryCsv.NONE, result.txTone);
        assertFalse(result.skipDuringScan);
    }

    @Test
    public void readsFileWithoutHeaderInDocumentedColumnOrder() {
        String csv = "Marine 16,156.8000,None,600,None,None,Marine,false\n";

        ChannelMemory result = ChannelMemoryCsv.fromCsv(csv).getMemories().get(0);

        assertEquals("Marine 16", result.name);
        assertEquals("156.8000", result.frequency);
        assertEquals("Marine", result.group);
    }

    @Test
    public void normalizesFrequencyShapesAndNamesUnnamedRows() {
        String csv = "name,frequency\r\n,145.5\r\n";

        ChannelMemory result = ChannelMemoryCsv.fromCsv(csv).getMemories().get(0);

        assertEquals("145.5000", result.frequency);
        assertEquals("145.5000", result.name);
    }

    @Test
    public void countsUnreadableRowsAndIgnoresBlankLines() {
        String csv = "name,frequency\r\nGood,145.5000\r\n\r\nBad,not-a-frequency\r\nAlsoBad,\r\n";

        ChannelMemoryCsv.ImportResult result = ChannelMemoryCsv.fromCsv(csv);

        assertEquals(1, result.getMemories().size());
        assertEquals(2, result.getSkippedRows());
    }

    @Test
    public void emptyInputYieldsNothingRatherThanFailing() {
        assertTrue(ChannelMemoryCsv.fromCsv("").getMemories().isEmpty());
        assertTrue(ChannelMemoryCsv.fromCsv(null).getMemories().isEmpty());
    }
}
