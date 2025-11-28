/*
Expense Tracker Application - Spring Boot + Hibernate + REST + MySQL
Project skeleton provided as a single-file reference you can split into packages/files in your IDE.

Packages (suggested):
 - com.codec.expensetracker
   - ExpenseTrackerApplication.java
   - controller (CategoryController, TransactionController, ReportController)
   - service (CategoryService, TransactionService, ReportService, impls)
   - repository (CategoryRepository, TransactionRepository, UserRepository)
   - model (User, Category, Transaction)
   - dto (TransactionRequest, TransactionResponse, ReportDTO)
   - exception (NotFoundException, GlobalExceptionHandler)

Notes:
- Use Spring Boot 3.x and Java 17+
- Add dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, mysql-connector-java, spring-boot-starter-validation, spring-boot-starter-actuator (optional), lombok (optional but used here for brevity)
- Passwords and real auth omitted for brevity; add Spring Security for production.

*/

// =====================
// ExpenseTrackerApplication.java
// =====================
package com.codec.expensetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExpenseTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }
}

// =====================
// model/User.java
// =====================
package com.codec.expensetracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // store hashed passwords in production

    private LocalDateTime createdAt = LocalDateTime.now();

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

// =====================
// model/Category.java
// =====================
package com.codec.expensetracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    // optional owner if multi-user
    // @ManyToOne private User owner;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

// =====================
// model/Transaction.java
// =====================
package com.codec.expensetracker.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String type; // EXPENSE or INCOME

    private String note;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // optionally link to a user
    // @ManyToOne private User user;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

// =====================
// repository/CategoryRepository.java
// =====================
package com.codec.expensetracker.repository;

import com.codec.expensetracker.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}

// =====================
// repository/TransactionRepository.java
// =====================
package com.codec.expensetracker.repository;

import com.codec.expensetracker.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByCategoryId(Long categoryId);

    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt ASC")
    List<Transaction> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.type = :type AND t.createdAt BETWEEN :from AND :to")
    java.math.BigDecimal sumByTypeBetween(@Param("type") String type, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}

// =====================
// service/CategoryService.java
// =====================
package com.codec.expensetracker.service;

import com.codec.expensetracker.model.Category;

import java.util.List;

public interface CategoryService {
    Category create(Category c);
    Category update(Long id, Category c);
    void delete(Long id);
    Category get(Long id);
    List<Category> listAll();
}

// =====================
// service/impl/CategoryServiceImpl.java
// =====================
package com.codec.expensetracker.service.impl;

import com.codec.expensetracker.exception.NotFoundException;
import com.codec.expensetracker.model.Category;
import com.codec.expensetracker.repository.CategoryRepository;
import com.codec.expensetracker.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository repo;
    public CategoryServiceImpl(CategoryRepository repo) { this.repo = repo; }

    @Override
    public Category create(Category c) { return repo.save(c); }

    @Override
    public Category update(Long id, Category c) {
        Category ex = repo.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
        ex.setName(c.getName()); ex.setDescription(c.getDescription());
        return repo.save(ex);
    }

    @Override
    public void delete(Long id) { repo.deleteById(id); }

    @Override
    public Category get(Long id) { return repo.findById(id).orElseThrow(() -> new NotFoundException("Category not found")); }

    @Override
    public List<Category> listAll() { return repo.findAll(); }
}

// =====================
// service/TransactionService.java
// =====================
package com.codec.expensetracker.service;

import com.codec.expensetracker.model.Transaction;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {
    Transaction create(Transaction t);
    Transaction get(Long id);
    void delete(Long id);
    List<Transaction> listAll();
    List<Transaction> listBetween(LocalDateTime from, LocalDateTime to);
}

// =====================
// service/impl/TransactionServiceImpl.java
// =====================
package com.codec.expensetracker.service.impl;

import com.codec.expensetracker.exception.NotFoundException;
import com.codec.expensetracker.model.Category;
import com.codec.expensetracker.model.Transaction;
import com.codec.expensetracker.repository.CategoryRepository;
import com.codec.expensetracker.repository.TransactionRepository;
import com.codec.expensetracker.service.TransactionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository trepo;
    private final CategoryRepository crepo;

    public TransactionServiceImpl(TransactionRepository trepo, CategoryRepository crepo) {
        this.trepo = trepo; this.crepo = crepo;
    }

    @Override
    public Transaction create(Transaction t) {
        if (t.getCategory()!=null && t.getCategory().getId()!=null) {
            Category c = crepo.findById(t.getCategory().getId()).orElseThrow(() -> new NotFoundException("Category not found"));
            t.setCategory(c);
        }
        return trepo.save(t);
    }

    @Override
    public Transaction get(Long id) { return trepo.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found")); }

    @Override
    public void delete(Long id) { trepo.deleteById(id); }

    @Override
    public List<Transaction> listAll() { return trepo.findAll(); }

    @Override
    public List<Transaction> listBetween(LocalDateTime from, LocalDateTime to) { return trepo.findBetween(from,to); }
}

// =====================
// service/ReportService.java
// =====================
package com.codec.expensetracker.service;

import com.codec.expensetracker.dto.ReportDTO;

import java.time.LocalDateTime;

public interface ReportService {
    ReportDTO generateSummary(LocalDateTime from, LocalDateTime to);
}

// =====================
// service/impl/ReportServiceImpl.java
// =====================
package com.codec.expensetracker.service.impl;

import com.codec.expensetracker.dto.ReportDTO;
import com.codec.expensetracker.service.ReportService;
import com.codec.expensetracker.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ReportServiceImpl implements ReportService {
    private final TransactionRepository trepo;
    public ReportServiceImpl(TransactionRepository trepo) { this.trepo = trepo; }

    @Override
    public ReportDTO generateSummary(LocalDateTime from, LocalDateTime to) {
        BigDecimal totalIncome = trepo.sumByTypeBetween("INCOME", from, to);
        BigDecimal totalExpense = trepo.sumByTypeBetween("EXPENSE", from, to);
        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpense == null) totalExpense = BigDecimal.ZERO;
        ReportDTO r = new ReportDTO();
        r.setFrom(from); r.setTo(to); r.setTotalIncome(totalIncome); r.setTotalExpense(totalExpense);
        r.setNet(totalIncome.subtract(totalExpense));
        return r;
    }
}

// =====================
// dto/TransactionRequest.java
// =====================
package com.codec.expensetracker.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class TransactionRequest {
    @NotNull
    private BigDecimal amount;
    @NotNull
    private String type; // EXPENSE/INCOME
    private String note;
    private Long categoryId;

    // getters/setters
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}

// =====================
// dto/TransactionResponse.java
// =====================
package com.codec.expensetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponse {
    private Long id;
    private BigDecimal amount;
    private String type;
    private String note;
    private Long categoryId;
    private String categoryName;
    private LocalDateTime createdAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

// =====================
// dto/ReportDTO.java
// =====================
package com.codec.expensetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReportDTO {
    private LocalDateTime from;
    private LocalDateTime to;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal net;

    // getters/setters
    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }
    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }
    public BigDecimal getTotalIncome() { return totalIncome; }
    public void setTotalIncome(BigDecimal totalIncome) { this.totalIncome = totalIncome; }
    public BigDecimal getTotalExpense() { return totalExpense; }
    public void setTotalExpense(BigDecimal totalExpense) { this.totalExpense = totalExpense; }
    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal net) { this.net = net; }
}

// =====================
// controller/CategoryController.java
// =====================
package com.codec.expensetracker.controller;

import com.codec.expensetracker.model.Category;
import com.codec.expensetracker.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Category> create(@RequestBody Category c) { return ResponseEntity.ok(service.create(c)); }

    @PutMapping("/{id}")
    public ResponseEntity<Category> update(@PathVariable Long id, @RequestBody Category c) { return ResponseEntity.ok(service.update(id,c)); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }

    @GetMapping("/{id}")
    public ResponseEntity<Category> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @GetMapping
    public ResponseEntity<List<Category>> list() { return ResponseEntity.ok(service.listAll()); }
}

// =====================
// controller/TransactionController.java
// =====================
package com.codec.expensetracker.controller;

import com.codec.expensetracker.dto.TransactionRequest;
import com.codec.expensetracker.dto.TransactionResponse;
import com.codec.expensetracker.model.Transaction;
import com.codec.expensetracker.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest req) {
        Transaction t = new Transaction();
        t.setAmount(req.getAmount()); t.setType(req.getType()); t.setNote(req.getNote());
        if (req.getCategoryId() != null) {
            t.setCategory(new com.codec.expensetracker.model.Category()); t.getCategory().setId(req.getCategoryId());
        }
        Transaction saved = service.create(t);
        return ResponseEntity.ok(toResp(saved));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        List<Transaction> list;
        if (from!=null && to!=null) {
            list = service.listBetween(LocalDateTime.parse(from), LocalDateTime.parse(to));
        } else list = service.listAll();
        return ResponseEntity.ok(list.stream().map(this::toResp).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> get(@PathVariable Long id) { return ResponseEntity.ok(toResp(service.get(id))); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }

    private TransactionResponse toResp(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.setId(t.getId()); r.setAmount(t.getAmount()); r.setType(t.getType()); r.setNote(t.getNote());
        r.setCreatedAt(t.getCreatedAt());
        if (t.getCategory()!=null) { r.setCategoryId(t.getCategory().getId()); r.setCategoryName(t.getCategory().getName()); }
        return r;
    }
}

// =====================
// controller/ReportController.java
// =====================
package com.codec.expensetracker.controller;

import com.codec.expensetracker.dto.ReportDTO;
import com.codec.expensetracker.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/summary")
    public ResponseEntity<ReportDTO> summary(@RequestParam String from, @RequestParam String to) {
        LocalDateTime f = LocalDateTime.parse(from);
        LocalDateTime t = LocalDateTime.parse(to);
        return ResponseEntity.ok(service.generateSummary(f,t));
    }
}

// =====================
// exception/NotFoundException.java
// =====================
package com.codec.expensetracker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    public NotFoundException(String msg) { super(msg); }
}

// =====================
// exception/GlobalExceptionHandler.java
// =====================
package com.codec.expensetracker.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> onNotFound(NotFoundException ex) { return ResponseEntity.status(404).body(ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> onValidation(MethodArgumentNotValidException ex) { return ResponseEntity.badRequest().body("Validation error: " + ex.getMessage()); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> onAny(Exception ex) { ex.printStackTrace(); return ResponseEntity.internalServerError().body("Internal: " + ex.getMessage()); }
}

/*
application.properties (src/main/resources/application.properties)
---------------------------------------------------------------
spring.datasource.url=jdbc:mysql://localhost:3306/expense_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=change_me
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jackson.serialization.WRITE_DATES_AS_TIMESTAMPS=false

# For production set ddl-auto to validate and use migrations (Flyway/Liquibase)


Database schema (if you prefer SQL):

CREATE DATABASE expense_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE expense_db;

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(150) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  description VARCHAR(500)
);

CREATE TABLE transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  amount DECIMAL(15,2) NOT NULL,
  type VARCHAR(20) NOT NULL,
  note TEXT,
  category_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (category_id) REFERENCES categories(id)
);


Quick curl examples:

# Create a category
curl -X POST -H "Content-Type: application/json" -d '{"name":"Food","description":"Meals"}' http://localhost:8080/api/categories

# Add transaction
curl -X POST -H "Content-Type: application/json" -d '{"amount":150.5,"type":"EXPENSE","note":"Lunch","categoryId":1}' http://localhost:8080/api/transactions

# Get transactions
curl http://localhost:8080/api/transactions

# Get report (ISO local datetime format)
curl 'http://localhost:8080/api/reports/summary?from=2025-01-01T00:00:00&to=2025-12-31T23:59:59'


Next steps & improvements:
 - Add user authentication & per-user isolation (JWT + Spring Security).
 - Add DTO mapping with MapStruct or ModelMapper for cleaner controllers.
 - Add pagination for transaction lists and filtering by category/type.
 - Add export (CSV/PDF) and charts for reports.
 - Add transaction recurring rules and budgets.
 - Add unit/integration tests and API documentation (SpringDoc/OpenAPI).

Good luck—tell me if you want this split into separate files and packaged into a Maven or Gradle project and I will create that structure for you.
*/
