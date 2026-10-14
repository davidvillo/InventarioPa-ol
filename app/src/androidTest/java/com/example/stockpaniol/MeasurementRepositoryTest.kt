package com.example.stockpaniol

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementRepositoryTest {
    private fun withRepository(block: (LocalStockRepository, android.content.Context) -> Unit) {
        val context = isolatedStockContext("measurements_test_")
        context.deleteDatabase("stock_paniol.db")
        try { LocalStockRepository(context).use { block(it, context) } }
        finally { context.deleteDatabase("stock_paniol.db") }
    }
    private fun entry(name: String, quantity: Double, unit: String) = StockEntry(0,name,quantity,unit,"","",todayStockDate(),"","","")
    private fun worker(r: LocalStockRepository): Long {
        r.addWorker(WorkerDraft("Ana","Ruiz",r.worksites().first().id,""))
        return r.personnel().single().id
    }
    private fun rejects(action: () -> Unit) = assertTrue(runCatching(action).isFailure)

    @Test fun litresSupportDecimalReceiptsIssuesAndReturnsWhilePairsRequireWholeNumbers() = withRepository { r, _ ->
        assertEquals(listOf("unidades","mts","kg","litros","pares"),r.measurements().map { it.name })
        r.receive(false,entry("Pintura",10.5,"litros"))
        r.receive(false,entry("Guantes",4.0,"pares"))
        rejects { r.receive(false,entry("Botas",1.5,"pares")) }
        val person=worker(r)
        val paint=r.items(false).first { it.name=="Pintura" }
        val gloves=r.items(false).first { it.name=="Guantes" }
        r.issue(false,paint.id,3.25,todayStockDate(),person,"")
        val paintLoan=r.movements(false).first { it.kind=="Egreso" }
        r.returnStock(false,paintLoan.id,1.25,todayStockDate(),person,"")
        assertEquals(8.5,r.items(false).first { it.id==paint.id }.quantity,0.0)
        rejects { r.issue(false,gloves.id,0.5,todayStockDate(),person,"") }
        r.issue(false,gloves.id,2.0,todayStockDate(),person,"")
        val gloveLoan=r.movements(false).first { it.kind=="Egreso" && it.itemId==gloves.id }
        rejects { r.returnStock(false,gloveLoan.id,0.5,todayStockDate(),person,"") }
        assertEquals(2.0,r.items(false).first { it.id==gloves.id }.quantity,0.0)
    }

    @Test fun unitCrudPreservesStockHistoryAndExistingReceiptsWhenRemoved() = withRepository { r, _ ->
        r.addMeasurement("cajas",true)
        val unit=r.measurements().first { it.name=="cajas" }
        r.receive(false,entry("Tornillos",5.0,"cajas"))
        val item=r.items(false).single()
        val person=worker(r)
        r.issue(false,item.id,2.0,todayStockDate(),person,"")
        val loan=r.movements(false).first { it.kind=="Egreso" }
        r.updateMeasurement(unit.id,"cajas chicas",true)
        assertEquals("cajas chicas",r.items(false).single().unit)
        assertEquals("cajas",r.movements(false).first { it.id==loan.id }.unit)
        rejects { r.updateMeasurement(unit.id,"cajas chicas",false) }
        r.deleteMeasurement(unit.id)
        assertFalse(r.measurements().any { it.id==unit.id })
        rejects { r.receive(false,entry("Clavos",2.0,"cajas chicas")) }
        r.receive(false,entry("Tornillos",1.0,"cajas chicas").copy(itemId=item.id))
        rejects { r.returnStock(false,loan.id,0.5,todayStockDate(),person,"") }
        r.returnStock(false,loan.id,2.0,todayStockDate(),person,"")
        assertEquals(6.0,r.items(false).single().quantity,0.0)
        r.addMeasurement("cajas chicas",true)
        assertEquals(unit.id,r.measurements().first { it.name=="cajas chicas" }.id)
        assertEquals(0,r.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use { it.count })
    }

    @Test fun renamedOrRemovedCountingUnitStillWorksForIdentifiedTools() = withRepository { r, _ ->
        val counting=r.measurements().first { it.counting }
        r.updateMeasurement(counting.id,"ejemplares",true)
        rejects { r.updateMeasurement(counting.id,"ejemplares",false) }
        r.deleteMeasurement(counting.id)
        r.receive(true,entry("Taladro",1.0,"ejemplares"))
        val item=r.items(true).single()
        val person=worker(r)
        r.issue(true,item.id,1.0,todayStockDate(),person,"",item.units.map { it.id })
        val loan=r.movements(true).first { it.kind=="Egreso" }
        r.returnStock(true,loan.id,1.0,todayStockDate(),null,"",loan.units.map { it.unit.id })
        assertEquals(1.0,r.items(true).single().quantity,0.0)
        assertTrue(r.items(true).single().wholeNumbers)
    }

    @Test fun blankAndDuplicateUnitsAreRejectedAndUnreferencedUnitsAreDeleted() = withRepository { r, _ ->
        rejects { r.addMeasurement(" ",false) }
        rejects { r.addMeasurement("Litros",false) }
        val kg=r.measurements().first { it.name=="kg" }
        rejects { r.updateMeasurement(kg.id,"pares",false) }
        r.addMeasurement("rollos",true)
        val roll=r.measurements().first { it.name=="rollos" }
        r.deleteMeasurement(roll.id)
        assertFalse(r.measurements(true).any { it.id==roll.id })
        assertEquals(5,r.measurements().size)
    }

    @Test fun versionFiveMigrationKeepsWorkersUnitsAndQuantityRules() = withRepository { r, context ->
        val person=worker(r)
        r.receive(false,entry("Arena",2.5,"kg"))
        r.issue(false,r.items(false).single().id,0.5,todayStockDate(),person,"")
        val before=r.movements(false)
        val worker=r.personnel().single()
        removeMeasurementSchema(r.writableDatabase)
        r.writableDatabase.version=5
        r.close()
        LocalStockRepository(context).use { upgraded ->
            assertEquals(6,upgraded.readableDatabase.version)
            assertEquals(worker,upgraded.personnel().single())
            assertEquals(before,upgraded.movements(false))
            assertEquals(2.0,upgraded.items(false).single().quantity,0.0)
            assertTrue(upgraded.measurements().any { it.name=="litros" })
            assertTrue(upgraded.measurements().any { it.name=="pares" })
        }
    }
}
