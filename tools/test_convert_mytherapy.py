import datetime as dt
import unittest
from convert_mytherapy import medication_rows

class Page:
    def __init__(self,frequency='1 comprimido(s) , 1 vezes ao dia, às 20:00',note='',section='Medicamentos atualmente programados'):
        self.cells=[(18,86,section),(43,132,'Medicamento de exemplo'),(266,132,frequency),(468,132,'-'),(623,132,note)]
    def extract_text(self):return 'Relatório de Saúde MyTherapy'
    def extract_words(self):
        return [{'text':text,'x0':x,'x1':x+30,'top':y,'bottom':y+7} for x,y,text in self.cells if text]

class ConversionTests(unittest.TestCase):
    def row(self,*args,date='2026-11-08',**kwargs):return medication_rows([Page(*args,**kwargs)],date)[0]
    def test_remaining_days_use_report_date(self):
        r=self.row(note='por 90 dias, 82 dias restantes')
        self.assertEqual(r['end_date'],'2027-01-29')
        self.assertEqual(r['remaining_days_from_date'],'2026-11-08')
        self.assertTrue(r['end_date_calculated'])
        self.assertEqual(r['review_reasons'],[])
    def test_second_duration(self):
        self.assertEqual(self.row(note='por 60 dias, 53 dias restantes')['end_date'],'2026-12-31')
    def test_calendar_rollovers(self):
        for start,remaining,expected in [('2024-02-28',2,'2024-03-01'),('2026-12-31',1,'2027-01-01'),('2026-11-08',0,'2026-11-08')]:
            with self.subTest(start=start):
                self.assertEqual(self.row(note=f'por 90 dias, {remaining} dias restantes',date=start)['end_date'],expected)
    def test_explicit_end_wins(self):
        r=self.row(note='termina a 15/12/2026, por 60 dias, 53 dias restantes')
        self.assertEqual(r['end_date'],'2026-12-15');self.assertFalse(r['end_date_calculated'])
    def test_weekday_and_unit_are_preserved(self):
        r=self.row(frequency='5 gota(s) , Quarta-feira , às 13:00')
        self.assertEqual((r['amount'],r['unit'],r['weekdays'],r['times']),(5,'gota(s)',[3],['13:00']))
    def test_two_daily_times_use_the_two_reported_endpoints(self):
        r=self.row(frequency='1 comprimido(s) , 2 vezes por dia entre as 09:00 e as 21:00')
        self.assertEqual(r['times'],['09:00','21:00']);self.assertEqual(r['review_reasons'],[])
    def test_incomplete_interval_needs_review(self):
        r=self.row(frequency='1 comprimido(s) , 3 vezes por dia entre as 09:00 e as 21:00')
        self.assertEqual(len(r['review_reasons']),1)
    def test_inactive_states(self):
        for note,state in [('em pausa','paused'),('terminado em 03/08/2026','ended'),('a partir de 01/01/2027','future')]:
            with self.subTest(state=state):self.assertEqual(self.row(note=note,section='Não ativo')['status'],state)
    def test_needed_quantity_is_not_invented(self):
        r=self.row(frequency='quando necessário',section='Medicamentos sob demanda')
        self.assertIsNone(r['amount']);self.assertEqual(r['times'],[]);self.assertEqual(r['weekdays'],[])
    def test_unknown_initial_date_stays_unknown(self):
        self.assertIsNone(self.row()['start_date'])
    def test_source_note_is_preserved(self):
        note='por 60 dias, 53 dias restantes'
        self.assertEqual(self.row(note=note)['notes'],note)

if __name__=='__main__':unittest.main()
