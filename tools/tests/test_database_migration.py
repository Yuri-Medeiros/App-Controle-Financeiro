"""Execute the production migration against the exported original SQLite schema."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'app/src/main/java/br/com/yurimedeiros/meufinanceiro/data'
SCHEMAS = ROOT / 'app/schemas/br.com.yurimedeiros.meufinanceiro.data.FinanceDatabase'


class DatabaseMigrationTest(unittest.TestCase):
    def test_upgrade_preserves_existing_state_and_matches_room_schema(self):
        original = json.loads((SCHEMAS / '1.json').read_text())['database']
        with sqlite3.connect(':memory:') as db:
            for entity in original['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            db.execute('INSERT INTO local_state VALUES (?, ?)', (1, 123456789))
            statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', (DATA / 'FinanceMigrations.kt').read_text())
            self.assertTrue(statements)
            for statement in statements:
                db.execute(statement)
            self.assertEqual(db.execute('SELECT * FROM local_state').fetchall(), [(1, 123456789)])
            # Compare migrated columns against the schema exported by Room during compilation.
            target = json.loads((SCHEMAS / '2.json').read_text())['database']
            for entity in target['entities']:
                columns = {row[1]: (row[2], bool(row[3])) for row in db.execute('PRAGMA table_info(' + entity['tableName'] + ')')}
                self.assertEqual(columns, {f['columnName']: (f['affinity'], f['notNull']) for f in entity['fields']})
            db.execute("INSERT INTO movements (payload, origin, status, category, friendlyName, confirmedAt, pairedMovementId) VALUES ('{}', 'sample', 'PENDING', '', '', 0, 0)")
            self.assertEqual(db.execute('SELECT COUNT(*) FROM movements').fetchone()[0], 1)


    def test_people_upgrade_preserves_existing_movements_and_enforces_unique_names(self):
        original = json.loads((SCHEMAS / '2.json').read_text())['database']
        with sqlite3.connect(':memory:') as db:
            for entity in original['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            db.execute('INSERT INTO local_state VALUES (?, ?)', (1, 123456789))
            db.execute("INSERT INTO movements (id, payload, origin, status, category, friendlyName, confirmedAt, pairedMovementId) VALUES (7, '{}', 'existing', 'CONFIRMED', 'Alimentação', 'Nome salvo', 123, 0)")
            db.execute("INSERT INTO import_documents (source, reference) VALUES ('existing-document', '2026-10-01')")
            original_movement = db.execute('SELECT * FROM movements').fetchall()
            migration = (DATA / 'FinanceMigrations.kt').read_text().split('val from2To3', 1)[1].split('val from3To4', 1)[0]
            statements = re.findall(r'db\.execSQL\("([^"\n]+)"\)', migration)
            self.assertEqual(len(statements), 2)
            for statement in statements:
                db.execute(statement)
            self.assertEqual(db.execute('SELECT * FROM local_state').fetchall(), [(1, 123456789)])
            self.assertEqual(db.execute('SELECT * FROM movements').fetchall(), original_movement)
            self.assertEqual(db.execute('SELECT * FROM import_documents').fetchall(), [('existing-document', '2026-10-01')])
            columns = {row[1]: (row[2], bool(row[3])) for row in db.execute('PRAGMA table_info(people)')}
            self.assertEqual(columns, {'id': ('INTEGER', True), 'name': ('TEXT', True), 'normalizedName': ('TEXT', True)})
            db.execute("INSERT INTO people (name, normalizedName) VALUES ('Pessoa Teste', 'pessoa teste')")
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute("INSERT INTO people (name, normalizedName) VALUES ('PESSOA TESTE', 'pessoa teste')")
            self.assertEqual(db.execute('SELECT COUNT(*) FROM people').fetchone()[0], 1)
