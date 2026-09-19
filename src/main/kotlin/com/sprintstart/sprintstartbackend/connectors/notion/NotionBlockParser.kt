package com.sprintstart.sprintstartbackend.connectors.notion

class NotionBlockParser {
    fun extractCellTexts(row: NotionTableRow): List<String>{
        val toReturn = mutableListOf<String>()
        for (cell in row.cells){
            toReturn.add(cell.joinToString(separator = ""){ fragment ->
                fragment.plainText
            }
            )
        }
        return toReturn
        }

    }
