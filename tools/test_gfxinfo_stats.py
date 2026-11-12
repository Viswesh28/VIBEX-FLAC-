import unittest
from gfxinfo_stats import frame_times, summarize
class StatsTest(unittest.TestCase):
    def test_synthetic_profile(self):
        sample='''---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted,\n0,1000000,11000000,\n0,2000000,32000000,\n1,2000000,40000000,\n0,10,1,\n---PROFILEDATA---\n'''
        values=frame_times(sample)
        self.assertEqual(values,[10.0,30.0]); out=summarize(values,60)
        self.assertEqual(out['median_ms'],20);self.assertEqual(out['p95_ms'],30);self.assertEqual(out['over_refresh_budget'],1)
    def test_missing_data_fails(self):
        self.assertEqual(frame_times('no trace'),[])
        with self.assertRaises(ValueError): summarize([],60)
if __name__=='__main__': unittest.main()
