package com.learnmart.app.di

import com.learnmart.app.data.repository.AssessmentRepositoryImpl
import com.learnmart.app.data.repository.OperationsRepositoryImpl
import com.learnmart.app.data.repository.AuditRepositoryImpl
import com.learnmart.app.data.repository.CartRepositoryImpl
import com.learnmart.app.data.repository.CourseRepositoryImpl
import com.learnmart.app.data.repository.EnrollmentRepositoryImpl
import com.learnmart.app.data.repository.InventoryRepositoryImpl
import com.learnmart.app.data.repository.OrderRepositoryImpl
import com.learnmart.app.data.repository.PaymentRepositoryImpl
import com.learnmart.app.data.repository.PolicyRepositoryImpl
import com.learnmart.app.data.repository.RoleRepositoryImpl
import com.learnmart.app.data.repository.UserRepositoryImpl
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.domain.repository.OperationsRepository
import com.learnmart.app.domain.repository.AuditRepository
import com.learnmart.app.domain.repository.CartRepository
import com.learnmart.app.domain.repository.CourseRepository
import com.learnmart.app.domain.repository.EnrollmentRepository
import com.learnmart.app.domain.repository.InventoryRepository
import com.learnmart.app.domain.repository.OrderRepository
import com.learnmart.app.domain.repository.PaymentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.domain.repository.RoleRepository
import com.learnmart.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindRoleRepository(impl: RoleRepositoryImpl): RoleRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds
    @Singleton
    abstract fun bindPolicyRepository(impl: PolicyRepositoryImpl): PolicyRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindEnrollmentRepository(impl: EnrollmentRepositoryImpl): EnrollmentRepository

    @Binds
    @Singleton
    abstract fun bindCartRepository(impl: CartRepositoryImpl): CartRepository

    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindAssessmentRepository(impl: AssessmentRepositoryImpl): AssessmentRepository

    @Binds
    @Singleton
    abstract fun bindOperationsRepository(impl: OperationsRepositoryImpl): OperationsRepository
}
