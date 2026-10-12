package com.invohub.backend.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** For background jobs, e.g. InvitationNotificationJob */
@Configuration
@EnableScheduling
class SchedulingConfig
