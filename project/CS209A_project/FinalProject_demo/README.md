# CS209A Final Project Demo

This is a simple Spring Boot project template designed to help you kickstart your CS209A final project — a web application for analyzing Stack Overflow Java Q&A data.

The demo includes:
- A basic homepage with a search bar and a pie chart.
- All code is written in Java using Spring Boot 3.5.7 and JDK 22.

---

## 🛠 Project Setup & Configuration

### Prerequisites
- **Java Development Kit (JDK) 22** (or higher)
- **IntelliJ IDEA** (Community or Ultimate Edition)

### Creating the Project from Scratch (Recommended)

If you prefer to create the project yourself (highly recommended for learning), follow these steps:

1. Open IntelliJ IDEA → **New Project** → Select **Spring Initializr**.
2. Configure the project as shown in the image below:

   ![Project Creation Settings](/imgs/proj_setting_0.png)

    - **Name**: `FinalProject_demo`
    - **Group**: `cs209a`
    - **Artifact**: `finalproject_demo`
    - **Package name**: `cs209a.finalproject_demo`
    - **JDK**: `openjdk-22 Oracle OpenJDK 22.0.1`
    - **Packaging**: `Jar`

3. Add the following dependencies:

   ![Dependencies Selection](/imgs/proj_setting_1.png)

    - **Spring Web**
    - **Thymeleaf**
    - **Spring Boot DevTools**

4. Click **Create** to generate the project.

---

## ▶️ How to Run the Project

1. Clone this repository (or create your own project based on the instructions above).
2. Open the project folder in IntelliJ IDEA.
3. Navigate to the main class: `src/main/java/cs209a/finalproject_demo/FinalProjectDemoApplication.java`.
4. Click the **Run** button (green triangle) next to the `main` method.

You will see logs similar to this in the console:

![Console Output](/imgs/cmd_output.png)

> ✅ Look for the line: `Tomcat started on port 8080 (http)` — this means your server is running!

---

## 🌐 Accessing the Frontend

Once the server is running, open your browser and visit:

```
http://localhost:8080
```

You should see the following homepage:

![Homepage Screenshot](/imgs/web_output.png)

This page includes:
- A **search bar** (placeholder functionality only).
- A **pie chart** showing "Thread Distribution by Type" (Type 1, Type 2, Type 3).

---

## 🧩 Next Steps for Your Final Project

This demo provides a solid foundation. To complete your project, you should:

1. Implement **real data collection** from Stack Overflow (at least 1000 threads).
2. Store the data in a **database** (e.g., PostgreSQL or MySQL).
3. Build **four core analyses** (Topic Trends, Co-occurrence, Multithreading Pitfalls, Solvable vs. Hard-to-Solve Questions).
4. Connect your frontend charts to **dynamic backend APIs**.
5. Ensure all analysis is **real-time** and **not precomputed**.


---

Happy coding! 🚀