package com.stockpulse.account.repository

import com.stockpulse.account.domain.Account
import com.stockpulse.account.domain.Ledger
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, Long>
interface LedgerRepository : JpaRepository<Ledger, Long>
