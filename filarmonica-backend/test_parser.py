import unittest
from app.parser import clean_text, normalize_time_ranges, normalize_orchestration, parse_program


class ParserTests(unittest.TestCase):
    def test_vertical_tab_is_linebreak(self):
        self.assertEqual(clean_text("A\x0bB"), "A\nB")

    def test_time_normalization(self):
        self.assertEqual(normalize_time_ranges("10-14"), "10:00–14:00")
        self.assertEqual(normalize_time_ranges("10:00 - 14:00"), "10:00–14:00")

    def test_compact_orchestration(self):
        self.assertEqual(
            normalize_orchestration("3222/4231/timp.perc/hp/va.vc.db"),
            "3 2 2 2; 4 2 3 1; timp.perc; Hp; Vla; Vc; Cb",
        )

    def fixture(self, date_cell, conductor="", soloist="", program=""):
        return {
            "title": "Artiști - STAGIUNEA 2026/2027",
            "tables": [{
                "table": 3,
                "rows": [
                    {"row": 1, "cells": ["NOV.", "Dirijor", "Solist", "Program"]},
                    {"row": 2, "cells": [date_cell, conductor, soloist, program]},
                ],
            }],
        }

    def test_multiple_concerts(self):
        p = parse_program(self.fixture("17+18", "X", "", "Concert de Crăciun"))
        e = p["months"][0]["weeks"][0]["events"][0]
        self.assertEqual([x["date"] for x in e["concerts"]], ["2026-11-17", "2026-11-18"])

    def test_uncertain_date_not_decided(self):
        p = parse_program(self.fixture("6(?)\n7", "X", "", "Muzică de film"))
        e = p["months"][0]["weeks"][0]["events"][0]
        self.assertTrue(e["date"]["uncertain"])
        self.assertEqual(e["date"]["dates"], [])
        self.assertEqual(e["date"]["candidate_dates"], ["2026-11-06", "2026-11-07"])

    def test_three_string_values_map_from_first_violin(self):
        p = parse_program(self.fixture(
            "6",
            "Lawrence Foster",
            "Jenő Koppándi vioară",
            "Dohnányi - Concertul pentru vioară nr. 2\n"
            "3222/4231/timp.perc/hp/va.vc.db\n"
            "Cordari:\nDohnányi 10 8 6\n"
            "Repetiții:\nLuni 10-14"
        ))
        e = p["months"][0]["weeks"][0]["events"][0]
        s = e["string_distributions"][0]
        self.assertEqual(s["display"], "Vioara I - 10 • Vioara II - 8 • Viola - 6")
        self.assertEqual(e["rehearsals"][0]["lines"][0]["display"], "10:00–14:00")


if __name__ == "__main__":
    unittest.main()
