import sys
import os
import re

import pandas as pd
import matplotlib.pyplot as plt


def safe_filename(name):
    """
    Convert operation ID into a safe filename.
    """
    return re.sub(r'[^a-zA-Z0-9_.-]', '_', str(name))


def create_output_directory(input_directory):
    charts_directory = os.path.join(input_directory, "charts")
    os.makedirs(charts_directory, exist_ok=True)
    return charts_directory


def plot_response_time_distribution(df, operation_id, output_file):
    data = df[
        df["operation_id"] == operation_id
    ]["response_time_ms"]

    if data.empty:
        return

    plt.figure(figsize=(10, 6))

    plt.hist(data, bins=50)

    plt.xlabel("Response time (ms)")
    plt.ylabel("Number of operations")

    if operation_id == "ALL":
        plt.title("Response Time Distribution - All Operations")
    else:
        plt.title(
            f"Response Time Distribution - {operation_id}"
        )

    plt.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(output_file, dpi=150)
    plt.close()


def create_response_time_charts(input_directory, charts_directory):

    file = os.path.join(
        input_directory,
        "response_times.csv"
    )

    df = pd.read_csv(file)

    # All operations
    plot_response_time_distribution(
        df,
        "ALL",
        os.path.join(
            charts_directory,
            "response_time_all.png"
        )
    )

    # Each operation
    operation_ids = df["operation_id"].unique()

    for operation_id in operation_ids:

        if operation_id == "ALL":
            continue

        plot_response_time_distribution(
            df,
            operation_id,
            os.path.join(
                charts_directory,
                f"response_time_{safe_filename(operation_id)}.png"
            )
        )


def create_lock_wait_chart(input_directory, charts_directory):

    file = os.path.join(
        input_directory,
        "lock_waiting.csv"
    )

    df = pd.read_csv(file)

    df = df[df["operation_id"] != "ALL"]

    if df.empty:
        return

    plt.figure(figsize=(12, 6))

    plt.bar(
        df["operation_id"].astype(str),
        df["percentage_waited"]
    )

    plt.xlabel("Operation")
    plt.ylabel("Operations waiting for locks (%)")
    plt.title("Percentage of Operations Waiting for Locks")

    plt.xticks(rotation=45, ha="right")

    plt.grid(
        axis="y",
        alpha=0.3
    )

    plt.tight_layout()

    plt.savefig(
        os.path.join(
            charts_directory,
            "lock_wait_percentage.png"
        ),
        dpi=150
    )

    plt.close()


def plot_throughput(
        df,
        operation_id,
        output_file
):
    data = df.copy()

    if data.empty:
        return

    data = data.sort_values("second")

    # Convert milliseconds since epoch to seconds
    start_time = data["second"].min()

    data["time_seconds"] = (
        data["second"] - start_time
    ) / 1000.0

    plt.figure(figsize=(12, 6))

    plt.plot(
        data["time_seconds"],
        data["count"]
    )

    plt.xlabel("Time (seconds)")
    plt.ylabel("Operations / second")

    if operation_id == "ALL":
        plt.title("Throughput - All Operations")
    else:
        plt.title(
            f"Throughput - {operation_id}"
        )

    plt.grid(True, alpha=0.3)

    plt.tight_layout()
    plt.savefig(output_file, dpi=150)
    plt.close()


def create_throughput_charts(input_directory, charts_directory):

    file_all = os.path.join(
        input_directory,
        "throughputAll.csv"
    )

    df_all = pd.read_csv(file_all)

    # All operations
    plot_throughput(
        df_all,
        "ALL",
        os.path.join(
            charts_directory,
            "throughput_all.png"
        )
    )


def main():

    print(len(sys.argv))
    if len(sys.argv) != 2:
        print(
            "Usage: python3 statistics_charts.py <results_directory>"
        )
        sys.exit(1)

    input_directory = sys.argv[1]

    if not os.path.isdir(input_directory):
        print(
            f"Results directory does not exist: {input_directory}"
        )
        sys.exit(1)

    charts_directory = create_output_directory(
        input_directory
    )

    create_response_time_charts(
        input_directory,
        charts_directory
    )

    create_lock_wait_chart(
        input_directory,
        charts_directory
    )

    create_throughput_charts(
        input_directory,
        charts_directory
    )

    print(
        f"Charts created in: {charts_directory}"
    )


if __name__ == "__main__":
    main()
