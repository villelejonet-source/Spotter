package com.viktorolsson.spotter.shell

import org.koin.core.module.Module

/**
 * Everything both apps inject: the platform's data module (`androidDataModule` or `dataModule`
 * around its DataGraph), then every screen's view models. Android's Application and iOS's
 * SpotterIos start Koin with this, so the two can't drift apart.
 */
fun spotterModules(data: Module): List<Module> = listOf(data) + uiModules
