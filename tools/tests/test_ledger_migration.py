"""Run the additive SQL migration against the actual exported Room schemas."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'app/schemas/br.com.yurimedeiros.meufinanceiro.data.FinanceDatabase'


class LedgerMigrationTest(unittest.TestCase):
    def test_schema_three_upgrade_preserves_confirmed_records_and_people(self):
        schema = json.loads((SCHEMAS / '3.json').read_text())['database']
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        for entity in schema['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        db.execute('INSERT INTO local_state VALUES (1, 123456)')
        db.execute("INSERT INTO people(name, normalizedName) VALUES ('Pessoa de teste', 'pessoa de teste')")
        db.execute("INSERT INTO movements(confirmedAt,pairedMovementId,payload,origin,status,category,friendlyName) VALUES (2,0,'preservar','origem','CONFIRMED','Casa','Nome')")
        source = (ROOT / 'app/src/main/java/br/com/yurimedeiros/meufinanceiro/data/FinanceMigrations.kt').read_text()
        migration = source.split('val from3To4 =', 1)[1]
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', migration):
            db.execute(sql)
        self.assertEqual(db.execute('SELECT createdAtMillis FROM local_state').fetchone(), (123456,))
        self.assertEqual(db.execute('SELECT payload,status,category,friendlyName FROM movements').fetchone(), ('preservar', 'CONFIRMED', 'Casa', 'Nome'))
        self.assertEqual(db.execute('SELECT name FROM people').fetchone(), ('Pessoa de teste',))
        self.assertEqual(db.execute('SELECT COUNT(*) FROM financial_events').fetchone(), (0,))
        expected = json.loads((SCHEMAS / '4.json').read_text())['database']
        for entity in expected['entities']:
            table = entity['tableName']
            columns = {row[1]: row[2] for row in db.execute(f'PRAGMA table_info({table})')}
            self.assertEqual(columns, {field['columnName']: field['affinity'] for field in entity['fields']})
