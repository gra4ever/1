import unittest

from app.parser_v04 import parse_program


class ParserV04Tests(unittest.TestCase):
    def fixture(self, header, rows):
        return {
            "title": "Artiști - STAGIUNEA 2026/2027",
            "tables": [{
                "table": 1,
                "rows": [{"row": 1, "cells": [header, "Dirijor", "Solist", "Program"]}] + [
                    {"row": i + 2, "cells": r} for i, r in enumerate(rows)
                ],
            }],
        }

    def test_cbas_and_monday_friday_week(self):
        p = parse_program(self.fixture("OCT.", [[
            "9", "X", "Y violoncel",
            "A - B\nCordari:\n14 12 10 8 6\nRepetiții:\nLuni 10-14\nVineri 10-13, 19-21"
        ]]))
        w = p["months"][0]["weeks"][0]
        self.assertEqual(w["label"], "5–9 octombrie")
        e = w["events"][0]
        self.assertIn("C-bas - 6", e["string_distributions"][0]["display"])
        self.assertIn("Concert 19:00–21:00", e["rehearsals"][-1]["lines"][0]["display"])

    def test_week_extends_for_sunday_concert(self):
        p = parse_program(self.fixture("OCT.", [["11", "X", "", "A - B"]]))
        self.assertEqual(p["months"][0]["weeks"][0]["label"], "5–11 octombrie")

    def test_tmc_orchestra_and_recital_split(self):
        p = parse_program(self.fixture("OCT.", [
            ["10\nTMC Sala Studio", "Dirijor X", "Solist Y vioară", "A - B"],
            ["11\nTMC Sala Studio", "", "Artist Z pian", "Recital"],
        ]))
        m = p["months"][0]
        orch = [e for w in m["weeks"] for e in w["events"]]
        self.assertEqual(len(orch), 1)
        self.assertEqual(orch[0]["type"], "orchestra")
        self.assertEqual(len(m["recitals"]), 1)
        self.assertEqual(m["recitals"][0]["type"], "recital")

    def test_april_29_evening_range_marked_concert(self):
        p = parse_program(self.fixture("APR.", [[
            "29", "X", "", "A - B\nRepetiții:\nJoi 10-13, 19-21"
        ]]))
        e = p["months"][0]["weeks"][0]["events"][0]
        self.assertEqual(e["concerts"][0]["time"], "19:00")
        self.assertIn("Concert 19:00–21:00", e["rehearsals"][0]["lines"][0]["display"])


if __name__ == "__main__":
    unittest.main()
